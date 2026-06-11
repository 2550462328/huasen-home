package com.huasen.portal;

import com.huasen.common.entity.ColumnEntity;
import com.huasen.common.entity.ColumnSite;
import com.huasen.common.entity.Site;
import com.huasen.common.exception.BusinessException;
import com.huasen.common.repository.ColumnRepository;
import com.huasen.common.repository.ColumnSiteRepository;
import com.huasen.common.repository.SiteRepository;
import com.huasen.common.service.QiniuStorageService;
import com.huasen.portal.service.SiteService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Service / integration tests for {@link SiteService#quickAdd(Map)}.
 *
 * <p>Covers EXT-10 atomicity, rollback, column-not-found, validation, icon degrade
 * and the Qiniu-bean-absent path. Uses an actual Spring context (MySQL+JPA) so we
 * can observe real transaction behavior; tests clean their own data in {@code @AfterEach}.
 *
 * <p>{@link QiniuStorageService} is provided by the {@code qiniu.access-key} property
 * in {@code application-dev.yml}, so it is present here as a {@link MockBean}. To exercise
 * the bean-absent path we set the field to {@code null} via reflection inside one test.
 */
@SpringBootTest
class SiteQuickAddTest {

    @Autowired
    private SiteService siteService;

    @Autowired
    private ColumnRepository columnRepository;

    @Autowired
    private SiteRepository siteRepository;

    @SpyBean
    private ColumnSiteRepository columnSiteRepository;

    @MockBean
    private QiniuStorageService qiniuStorageService;

    private ColumnEntity seededColumn;

    @BeforeEach
    void seedColumn() {
        seededColumn = columnRepository.save(QuickAddTestSupport.newColumn("快速添加测试栏目-" + System.nanoTime()));
        reset(columnSiteRepository);
    }

    @AfterEach
    void cleanup() {
        // Drop bindings + sites + the seeded column we created. Best effort: any test that
        // already cleaned up in-flight is a no-op.
        for (ColumnSite cs : columnSiteRepository.findByColumnId(seededColumn.getId())) {
            columnSiteRepository.delete(cs);
        }
        siteRepository.findAll().stream()
                .filter(s -> s.getName() != null && s.getName().startsWith("__quickAddTest__"))
                .forEach(siteRepository::delete);
        if (seededColumn.getId() != null && columnRepository.existsById(seededColumn.getId())) {
            columnRepository.deleteById(seededColumn.getId());
        }
    }

    /* ------------------------------------------------------------------ */
    /* Atomicity / happy path                                              */
    /* ------------------------------------------------------------------ */

    @Test
    void atomic_create_and_bind_persists_site_and_columnSite() {
        Map<String, Object> params = QuickAddTestSupport.newQuickAddParams(
                "__quickAddTest__atomic", "https://atomic.example.com", null, seededColumn.getId());

        Site result = siteService.quickAdd(params);

        assertNotNull(result, "quickAdd returns the created Site (D-11)");
        assertNotNull(result.getId(), "Site is persisted (has assigned id)");
        assertEquals("__quickAddTest__atomic", result.getName());
        assertEquals("https://atomic.example.com", result.getUrl());

        Site reloaded = siteRepository.findById(result.getId()).orElseThrow();
        assertEquals("__quickAddTest__atomic", reloaded.getName());

        List<ColumnSite> bindings = columnSiteRepository.findBySiteId(result.getId());
        assertEquals(1, bindings.size(), "Exactly one ColumnSite binding is written");
        assertEquals(seededColumn.getId(), bindings.get(0).getColumn().getId(),
                "Binding references the requested column");
    }

    /* ------------------------------------------------------------------ */
    /* Rollback                                                            */
    /* ------------------------------------------------------------------ */

    @Test
    void binding_failure_rolls_back_site_row() {
        long sitesBefore = siteRepository.count();

        // Force columnSiteRepository.save to throw — site row should be rolled back.
        doThrow(new RuntimeException("simulated binding failure"))
                .when(columnSiteRepository).save(any(ColumnSite.class));

        Map<String, Object> params = QuickAddTestSupport.newQuickAddParams(
                "__quickAddTest__rollback", "https://rollback.example.com", null, seededColumn.getId());

        assertThrows(RuntimeException.class, () -> siteService.quickAdd(params));

        assertEquals(sitesBefore, siteRepository.count(),
                "Binding failure must roll back the site row (full atomicity)");
        assertFalse(
                siteRepository.findAll().stream()
                        .anyMatch(s -> "__quickAddTest__rollback".equals(s.getName())),
                "No site row remains after binding failure");
    }

    /* ------------------------------------------------------------------ */
    /* Column not found                                                    */
    /* ------------------------------------------------------------------ */

    @Test
    void columnId_not_in_db_throws_BusinessException_and_persists_nothing() {
        long sitesBefore = siteRepository.count();
        Long missingColumnId = 9_999_999L;

        Map<String, Object> params = QuickAddTestSupport.newQuickAddParams(
                "__quickAddTest__missingColumn", "https://x.example.com", null, missingColumnId);

        BusinessException ex = assertThrows(BusinessException.class, () -> siteService.quickAdd(params));
        assertEquals("ERROR", ex.getTag());
        assertEquals("栏目不存在", ex.getMessage());

        assertEquals(sitesBefore, siteRepository.count(),
                "No site is persisted when columnId pre-check fails");
        verify(columnSiteRepository, never()).save(any(ColumnSite.class));
    }

    /* ------------------------------------------------------------------ */
    /* Validation (no writes on bad input)                                 */
    /* ------------------------------------------------------------------ */

    @Test
    void blank_name_throws_BusinessException_before_any_write() {
        long sitesBefore = siteRepository.count();
        Map<String, Object> params = QuickAddTestSupport.newQuickAddParams(
                "   ", "https://x.example.com", null, seededColumn.getId());
        BusinessException ex = assertThrows(BusinessException.class, () -> siteService.quickAdd(params));
        assertEquals("ERROR", ex.getTag());
        assertEquals("名称不能为空", ex.getMessage());
        assertEquals(sitesBefore, siteRepository.count());
    }

    @Test
    void null_name_throws_BusinessException() {
        Map<String, Object> params = QuickAddTestSupport.newQuickAddParams(
                null, "https://x.example.com", null, seededColumn.getId());
        BusinessException ex = assertThrows(BusinessException.class, () -> siteService.quickAdd(params));
        assertEquals("名称不能为空", ex.getMessage());
    }

    @Test
    void non_http_url_throws_BusinessException_before_any_write() {
        long sitesBefore = siteRepository.count();
        Map<String, Object> params = QuickAddTestSupport.newQuickAddParams(
                "__quickAddTest__badUrl", "ftp://nope.example.com", null, seededColumn.getId());
        BusinessException ex = assertThrows(BusinessException.class, () -> siteService.quickAdd(params));
        assertEquals("ERROR", ex.getTag());
        assertEquals("URL必须以http://或https://开头", ex.getMessage());
        assertEquals(sitesBefore, siteRepository.count());
    }

    @Test
    void null_columnId_throws_columnNotFound() {
        Map<String, Object> params = QuickAddTestSupport.newQuickAddParams(
                "__quickAddTest__nullCol", "https://x.example.com", null, null);
        BusinessException ex = assertThrows(BusinessException.class, () -> siteService.quickAdd(params));
        assertEquals("栏目不存在", ex.getMessage());
    }

    @Test
    void unparseable_columnId_throws_columnNotFound() {
        // ParamUtil.getLong tolerates bad strings -> default null -> 栏目不存在
        Map<String, Object> params = new HashMap<>();
        params.put("name", "__quickAddTest__badColId");
        params.put("url", "https://x.example.com");
        params.put("columnId", "not-a-number");

        BusinessException ex = assertThrows(BusinessException.class, () -> siteService.quickAdd(params));
        assertEquals("栏目不存在", ex.getMessage());
    }

    /* ------------------------------------------------------------------ */
    /* Icon degrade                                                        */
    /* ------------------------------------------------------------------ */

    @Test
    void qiniu_upload_failure_degrades_to_empty_icon_with_site_persisted() throws Exception {
        // Force Qiniu upload to throw — service must catch and persist site+binding anyway,
        // with site.icon left empty (D-13). Use a clearly-unreachable URL to avoid leaking
        // outbound traffic, then have Qiniu also throw if download somehow succeeds.
        when(qiniuStorageService.upload(any(byte[].class), any(String.class), any(String.class)))
                .thenThrow(new IOException("simulated qiniu failure"));

        Map<String, Object> params = QuickAddTestSupport.newQuickAddParams(
                "__quickAddTest__iconDegrade",
                "https://degrade.example.com",
                "http://127.0.0.1:1/icon-not-reachable.ico",
                seededColumn.getId());

        Site result = siteService.quickAdd(params);

        assertNotNull(result.getId(), "Site is persisted despite icon failure");
        Site reloaded = siteRepository.findById(result.getId()).orElseThrow();
        String icon = reloaded.getIcon();
        assertTrue(icon == null || icon.isEmpty(),
                "site.icon stays empty after icon-stage failure (D-13), got=" + icon);

        assertEquals(1, columnSiteRepository.findBySiteId(result.getId()).size(),
                "Binding still persisted after icon failure");
    }

    @Test
    void qiniu_bean_absent_does_not_throw_and_persists_site_binding() throws Exception {
        // Wipe the qiniuStorageService field on SiteService to simulate the absent-bean path.
        java.lang.reflect.Field f = SiteService.class.getDeclaredField("qiniuStorageService");
        f.setAccessible(true);
        Object original = f.get(siteService);
        f.set(siteService, null);
        try {
            Map<String, Object> params = QuickAddTestSupport.newQuickAddParams(
                    "__quickAddTest__noQiniu",
                    "https://no-qiniu.example.com",
                    "http://127.0.0.1:1/icon.ico",
                    seededColumn.getId());

            Site result = siteService.quickAdd(params);

            assertNotNull(result.getId());
            Site reloaded = siteRepository.findById(result.getId()).orElseThrow();
            String icon = reloaded.getIcon();
            assertTrue(icon == null || icon.isEmpty(),
                    "icon stays empty when QiniuStorageService bean is absent");
            assertEquals(1, columnSiteRepository.findBySiteId(result.getId()).size());
        } finally {
            f.set(siteService, original);
        }
    }
}
