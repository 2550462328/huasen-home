package com.huasen.portal;

import com.huasen.common.entity.ColumnEntity;
import com.huasen.common.entity.Site;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared fixtures + factories for quick-add tests.
 *
 * <p>This is the FIRST test class in {@code backend/src/test/java}; it doubles as the
 * smoke test that proves the test source set compiles and Surefire picks it up under
 * {@code mvn -pl backend test}. JUnit 5 + Spring Boot Test are already on the classpath
 * via {@code spring-boot-starter-test} (backend/pom.xml lines 115-119) — no new deps.
 *
 * <p>Plain helper class (not {@code @SpringBootTest}); consumed by service-layer tests
 * such as {@link SiteQuickAddTest}. Place under {@code com.huasen.portal} mirroring
 * the main source package layout.
 */
public class QuickAddTestSupport {

    /**
     * Build a minimal valid persistable {@link ColumnEntity}: required {@code name}
     * is set, {@code enabled}/{@code code} use field defaults (true / 0). Suitable
     * for {@code columnRepository.save(...)} in {@code @SpringBootTest} integration
     * tests. {@code id} stays null until the JPA save assigns one.
     */
    public static ColumnEntity newColumn(String name) {
        ColumnEntity column = new ColumnEntity();
        column.setName(name);
        return column;
    }

    /**
     * Build a {@link Site} with the two required non-null DB columns ({@code name},
     * {@code url}); {@code enabled}/{@code code} default via field initializers.
     */
    public static Site newSite(String name, String url) {
        Site site = new Site();
        site.setName(name);
        site.setUrl(url);
        return site;
    }

    /**
     * Build a quick-add params {@code Map<String,Object>} with the four keys the
     * extension sends: {@code name}, {@code url}, {@code icon}, {@code columnId}.
     * Any param may be null to model the validation paths.
     */
    public static Map<String, Object> newQuickAddParams(String name, String url, String icon, Long columnId) {
        Map<String, Object> params = new HashMap<>();
        params.put("name", name);
        params.put("url", url);
        params.put("icon", icon);
        params.put("columnId", columnId);
        return params;
    }

    /**
     * Smoke test — only here so this class is picked up by Surefire and proves the
     * test source set compiles and runs. Remove or supersede once richer tests in
     * sibling files (e.g. SiteQuickAddTest) carry the load.
     */
    @Test
    void factoriesProduceNonNullEntities() {
        ColumnEntity column = newColumn("测试栏目");
        assertNotNull(column);
        assertEquals("测试栏目", column.getName());
        assertTrue(column.getEnabled(), "ColumnEntity.enabled defaults to true");
        assertEquals(0, column.getCode(), "ColumnEntity.code defaults to 0");

        Site site = newSite("百度", "https://www.baidu.com");
        assertNotNull(site);
        assertEquals("百度", site.getName());
        assertEquals("https://www.baidu.com", site.getUrl());
        assertTrue(site.getEnabled(), "Site.enabled defaults to true");
        assertEquals(0, site.getCode(), "Site.code defaults to 0");

        Map<String, Object> params = newQuickAddParams("百度", "https://www.baidu.com", "https://x/icon.ico", 7L);
        assertNotNull(params);
        assertEquals("百度", params.get("name"));
        assertEquals("https://www.baidu.com", params.get("url"));
        assertEquals("https://x/icon.ico", params.get("icon"));
        assertEquals(7L, params.get("columnId"));
    }
}
