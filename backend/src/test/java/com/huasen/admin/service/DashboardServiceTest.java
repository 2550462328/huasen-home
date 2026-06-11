package com.huasen.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huasen.blog.sharon.entity.BlogPost;
import com.huasen.blog.sharon.repository.BlogPostRepository;
import com.huasen.blog.tiny.entity.TinyBlogPost;
import com.huasen.blog.tiny.repository.TinyBlogPostRepository;
import com.huasen.common.entity.DailyMetricSnapshot;
import com.huasen.common.repository.*;
import com.huasen.common.service.RedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DashboardService 单元测试 (Phase 13, Plan 04)
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private SiteRepository siteRepository;

    @Mock
    private ColumnRepository columnRepository;

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DailyMetricSnapshotRepository snapshotRepository;

    @Mock
    private BlogPostRepository blogPostRepository;

    @Mock
    private TinyBlogPostRepository tinyBlogPostRepository;

    @Mock
    private RedisService redisService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        // Reset mock interactions before each test
        reset(redisService, objectMapper);
    }

    @Test
    void getOverview_shouldReturnCounts_whenCacheMiss() throws Exception {
        // Given: cache miss
        when(redisService.get(anyString())).thenReturn(null);

        // And: repository counts
        when(siteRepository.count()).thenReturn(25L);
        when(columnRepository.count()).thenReturn(8L);
        when(articleRepository.count()).thenReturn(150L);
        when(userRepository.count()).thenReturn(42L);

        // And: ObjectMapper serialization
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"siteCount\":25}");

        // When
        Map<String, Object> result = dashboardService.getOverview();

        // Then: returns correct counts
        assertThat(result).containsEntry("siteCount", 25L);
        assertThat(result).containsEntry("columnCount", 8L);
        assertThat(result).containsEntry("articleCount", 150L);
        assertThat(result).containsEntry("userCount", 42L);

        // And: cache was queried
        verify(redisService).get(contains("overview"));

        // And: result was cached with 5min TTL
        verify(redisService).set(contains("overview"), anyString(), eq(5L), any());
    }

    @Test
    void getOverview_shouldReturnCachedResult_whenCacheHit() throws Exception {
        // Given: cache hit
        String cached = "{\"siteCount\":25,\"columnCount\":8,\"articleCount\":150,\"userCount\":42}";
        when(redisService.get(anyString())).thenReturn(cached);

        // And: ObjectMapper deserialization
        @SuppressWarnings("unchecked")
        Map<String, Object> cachedMap = Map.of(
            "siteCount", 25,
            "columnCount", 8,
            "articleCount", 150,
            "userCount", 42
        );
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(cachedMap);

        // When
        Map<String, Object> result = dashboardService.getOverview();

        // Then: returns cached data
        assertThat(result).hasSize(4);
        assertThat(result).containsEntry("siteCount", 25);

        // And: repositories were NOT called
        verify(siteRepository, never()).count();
        verify(columnRepository, never()).count();
    }

    @Test
    void getPvTrend_shouldReturnTimeSeriesArrays_whenCacheMiss() throws Exception {
        // Given: cache miss
        when(redisService.get(anyString())).thenReturn(null);

        // And: snapshot data (2 days, 4 metrics each)
        List<DailyMetricSnapshot> snapshots = new ArrayList<>();
        snapshots.add(createSnapshot("2026-06-01", "PV_USER", 120L));
        snapshots.add(createSnapshot("2026-06-01", "PV_MANAGE", 45L));
        snapshots.add(createSnapshot("2026-06-01", "PV_OTHER", 30L));
        snapshots.add(createSnapshot("2026-06-01", "UV", 150L));
        snapshots.add(createSnapshot("2026-06-02", "PV_USER", 135L));
        snapshots.add(createSnapshot("2026-06-02", "PV_MANAGE", 50L));
        snapshots.add(createSnapshot("2026-06-02", "PV_OTHER", 28L));
        snapshots.add(createSnapshot("2026-06-02", "UV", 165L));

        when(snapshotRepository.findAllByOrderByMetricDateAsc()).thenReturn(snapshots);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        // When
        Map<String, Object> result = dashboardService.getPvTrend();

        // Then: returns parallel arrays
        assertThat(result).containsKey("dates");
        assertThat(result).containsKey("user");
        assertThat(result).containsKey("manage");
        assertThat(result).containsKey("other");
        assertThat(result).containsKey("uv");

        @SuppressWarnings("unchecked")
        List<String> dates = (List<String>) result.get("dates");
        assertThat(dates).containsExactly("2026-06-01", "2026-06-02");

        @SuppressWarnings("unchecked")
        List<Long> user = (List<Long>) result.get("user");
        assertThat(user).containsExactly(120L, 135L);

        @SuppressWarnings("unchecked")
        List<Long> uv = (List<Long>) result.get("uv");
        assertThat(uv).containsExactly(150L, 165L);

        // And: cache was set
        verify(redisService).set(contains("pvTrend"), anyString(), eq(5L), any());
    }

    @Test
    void getArticleRank_shouldMergeAndSort_whenCacheMiss() throws Exception {
        // Given: cache miss
        when(redisService.get(anyString())).thenReturn(null);

        // And: sharon articles
        List<BlogPost> sharonPosts = new ArrayList<>();
        sharonPosts.add(createBlogPost(1L, "Sharon Hot", 500L));
        sharonPosts.add(createBlogPost(2L, "Sharon Medium", 200L));

        // And: tiny articles
        List<TinyBlogPost> tinyPosts = new ArrayList<>();
        tinyPosts.add(createTinyPost(10L, "Tiny Viral", 800));
        tinyPosts.add(createTinyPost(11L, "Tiny Low", 100));

        when(blogPostRepository.findTop10ByOrderByPostViewsDesc()).thenReturn(sharonPosts);
        when(tinyBlogPostRepository.findTop10ByOrderByVisitCountDesc()).thenReturn(tinyPosts);
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");

        // When
        List<Map<String, Object>> result = dashboardService.getArticleRank(10);

        // Then: returns merged TopN sorted by views DESC
        assertThat(result).hasSize(4); // 2 sharon + 2 tiny

        // First should be Tiny Viral (800 views)
        assertThat(result.get(0)).containsEntry("title", "Tiny Viral");
        assertThat(result.get(0)).containsEntry("views", 800L);
        assertThat(result.get(0)).containsEntry("source", "tiny");

        // Second should be Sharon Hot (500 views)
        assertThat(result.get(1)).containsEntry("title", "Sharon Hot");
        assertThat(result.get(1)).containsEntry("views", 500L);
        assertThat(result.get(1)).containsEntry("source", "sharon");

        // And: cache was set
        verify(redisService).set(contains("articleRank"), anyString(), eq(5L), any());
    }

    @Test
    void getArticleRank_shouldHandleNullViews() throws Exception {
        // Given: cache miss
        when(redisService.get(anyString())).thenReturn(null);

        // And: articles with null views
        List<BlogPost> sharonPosts = List.of(createBlogPost(1L, "No Views", null));
        List<TinyBlogPost> tinyPosts = List.of(createTinyPost(10L, "Also None", null));

        when(blogPostRepository.findTop10ByOrderByPostViewsDesc()).thenReturn(sharonPosts);
        when(tinyBlogPostRepository.findTop10ByOrderByVisitCountDesc()).thenReturn(tinyPosts);
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");

        // When
        List<Map<String, Object>> result = dashboardService.getArticleRank(10);

        // Then: null views normalized to 0
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).containsEntry("views", 0L);
        assertThat(result.get(1)).containsEntry("views", 0L);
    }

    @Test
    void getArticleRank_shouldRespectLimit() throws Exception {
        // Given: cache miss
        when(redisService.get(anyString())).thenReturn(null);

        // And: many articles
        List<BlogPost> sharonPosts = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            sharonPosts.add(createBlogPost((long) i, "Sharon " + i, (long) (100 - i * 10)));
        }

        List<TinyBlogPost> tinyPosts = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            tinyPosts.add(createTinyPost((long) (100 + i), "Tiny " + i, 90 - i * 10));
        }

        when(blogPostRepository.findTop10ByOrderByPostViewsDesc()).thenReturn(sharonPosts);
        when(tinyBlogPostRepository.findTop10ByOrderByVisitCountDesc()).thenReturn(tinyPosts);
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");

        // When: limit to 5
        List<Map<String, Object>> result = dashboardService.getArticleRank(5);

        // Then: returns only top 5
        assertThat(result).hasSize(5);

        // And: sorted by views DESC
        long prevViews = Long.MAX_VALUE;
        for (Map<String, Object> article : result) {
            long views = (Long) article.get("views");
            assertThat(views).isLessThanOrEqualTo(prevViews);
            prevViews = views;
        }
    }

    // Helper methods to create test entities
    private DailyMetricSnapshot createSnapshot(String date, String type, Long value) {
        DailyMetricSnapshot snapshot = new DailyMetricSnapshot();
        snapshot.setMetricDate(LocalDate.parse(date));
        snapshot.setMetricType(type);
        snapshot.setMetricValue(value);
        return snapshot;
    }

    private BlogPost createBlogPost(Long id, String title, Long views) {
        BlogPost post = new BlogPost();
        post.setPostId(id);
        post.setPostTitle(title);
        post.setPostViews(views);
        return post;
    }

    private TinyBlogPost createTinyPost(Long id, String title, Integer visitCount) {
        TinyBlogPost post = new TinyBlogPost();
        post.setId(id);
        post.setTitle(title);
        post.setVisitCount(visitCount);
        return post;
    }
}
