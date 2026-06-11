package com.huasen.portal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huasen.common.entity.Site;
import com.huasen.common.filter.BlacklistFilter;
import com.huasen.common.filter.FilterConfig;
import com.huasen.common.filter.JwtAuthFilter;
import com.huasen.common.filter.RequestParamsFilter;
import com.huasen.portal.controller.SiteController;
import com.huasen.portal.service.SiteService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-layer tests for {@code POST /site/quick-add} (EXT-11).
 *
 * <p>Establishes the {@code @WebMvcTest} + MockMvc convention for this backend
 * (no prior controller test existed). The auth guard reads the request attribute
 * {@code "huasenJWT_code"} (set server-side by {@code JwtAuthFilter}), so we drive
 * the guard via {@code .requestAttr(...)} and disable the servlet filter chain with
 * {@code @AutoConfigureMockMvc(addFilters = false)} — the RSA/JWT filters are not
 * under test here and would otherwise interfere.
 *
 * <p>{@link SiteService} is mocked: this slice proves the guard + plaintext JSON
 * parsing + delegation, NOT the service behavior (covered by SiteQuickAddTest).
 */
@WebMvcTest(
        controllers = SiteController.class,
        // The custom servlet Filters (@Component) are web-layer beans the @WebMvcTest slice
        // would otherwise instantiate; BlacklistFilter needs a RedisTemplate that is not part
        // of this slice. Exclude them (and their registration @Configuration) — addFilters=false
        // already keeps them out of the MockMvc chain, this keeps them out of the context.
        excludeFilters = @Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {BlacklistFilter.class, JwtAuthFilter.class,
                        RequestParamsFilter.class, FilterConfig.class}))
@AutoConfigureMockMvc(addFilters = false)
class SiteQuickAddControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SiteService siteService;

    private static final String URL = "/site/quick-add";

    /** Non-admin codes (and null) must be rejected with 403 and never reach the service. */
    @Test
    void forbidden_whenCodeNull() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"url\":\"https://x.com\"}"))
                // no requestAttr -> huasenJWT_code is null
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("请求禁止·权限不足"));

        verify(siteService, never()).quickAdd(any());
    }

    @Test
    void forbidden_whenCode0() throws Exception {
        assertForbiddenForCode(0);
    }

    @Test
    void forbidden_whenCode1() throws Exception {
        assertForbiddenForCode(1);
    }

    @Test
    void forbidden_whenCode2() throws Exception {
        assertForbiddenForCode(2);
    }

    private void assertForbiddenForCode(int code) throws Exception {
        mockMvc.perform(post(URL)
                        .requestAttr("huasenJWT_code", code)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"url\":\"https://x.com\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.msg").value("请求禁止·权限不足"));

        verify(siteService, never()).quickAdd(any());
    }

    /** Admin (code >= 3) reaches the service and the created Site is returned (D-11). */
    @Test
    void success_whenCode3_returnsSite() throws Exception {
        Site fixture = QuickAddTestSupport.newSite("百度", "https://www.baidu.com");
        when(siteService.quickAdd(any())).thenReturn(fixture);

        mockMvc.perform(post(URL)
                        .requestAttr("huasenJWT_code", 3)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                QuickAddTestSupport.newQuickAddParams(
                                        "百度", "https://www.baidu.com", null, 7L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("请求成功·快速添加成功"))
                .andExpect(jsonPath("$.data.name").value("百度"))
                .andExpect(jsonPath("$.data.url").value("https://www.baidu.com"));

        verify(siteService, times(1)).quickAdd(any());
    }

    /**
     * Plaintext JSON {name,url,icon,columnId} is parsed straight through (no
     * secretMethod/RSA transformation): the captured Map carries all four keys.
     */
    @Test
    @SuppressWarnings("unchecked")
    void plaintextJson_parsedIntoMap_noRsaPath() throws Exception {
        when(siteService.quickAdd(any())).thenReturn(
                QuickAddTestSupport.newSite("掘金", "https://juejin.cn"));

        String plaintextBody = "{"
                + "\"name\":\"掘金\","
                + "\"url\":\"https://juejin.cn\","
                + "\"icon\":\"https://juejin.cn/favicon.ico\","
                + "\"columnId\":42"
                + "}";

        mockMvc.perform(post(URL)
                        .requestAttr("huasenJWT_code", 3)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(plaintextBody))
                .andExpect(status().isOk());

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(siteService).quickAdd(captor.capture());

        Map<String, Object> parsed = captor.getValue();
        assertEquals("掘金", parsed.get("name"));
        assertEquals("https://juejin.cn", parsed.get("url"));
        assertEquals("https://juejin.cn/favicon.ico", parsed.get("icon"));
        assertEquals(42, ((Number) parsed.get("columnId")).intValue());
    }
}
