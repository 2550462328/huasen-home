package com.huasen.blog.sharon.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.huasen.blog.sharon.entity.BlogPost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 博客搜索服务
 * 基于Elasticsearch实现全文搜索
 * ES不可用时返回不可用状态(D-12)
 * 复用现有ES索引结构(D-13): index "post", fields: post-id, post-title, post-content, post-url, post-summary, post-date, tags
 */
@Service
public class BlogSearchService {

    private static final Logger logger = LoggerFactory.getLogger(BlogSearchService.class);

    private static final String INDEX_NAME = "post";

    @Autowired(required = false)
    private ElasticsearchClient elasticsearchClient;

    /**
     * 检查ES搜索服务是否可用
     * @return true if ES client exists and connection is healthy
     */
    public boolean isAvailable() {
        if (elasticsearchClient == null) {
            return false;
        }
        try {
            return elasticsearchClient.ping().value();
        } catch (Exception e) {
            logger.warn("Elasticsearch health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 全文搜索文章
     * 按D-13: 查询index "post", should match on "post-content" + should match on "post-title"
     * @param keyword 搜索关键词
     * @param page 页码(从1开始)
     * @param size 每页条数
     * @return 分页搜索结果
     */
    public Page<BlogPost> search(String keyword, int page, int size) {
        if (elasticsearchClient == null) {
            return Page.empty();
        }

        try {
            // Build bool query: should match on post-content and post-title
            Query titleQuery = MatchQuery.of(m -> m
                    .field("post-title")
                    .query(keyword)
            )._toQuery();

            Query contentQuery = MatchQuery.of(m -> m
                    .field("post-content")
                    .query(keyword)
            )._toQuery();

            BoolQuery boolQuery = BoolQuery.of(b -> b
                    .should(titleQuery)
                    .should(contentQuery)
                    .minimumShouldMatch("1")
            );

            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index(INDEX_NAME)
                    .query(boolQuery._toQuery())
                    .from((Math.max(0, page - 1)) * size)
                    .size(size)
            );

            SearchResponse<Map> response = elasticsearchClient.search(searchRequest, Map.class);

            List<BlogPost> posts = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                Map<String, Object> source = hit.source();
                if (source != null) {
                    BlogPost post = mapToPost(source);
                    posts.add(post);
                }
            }

            long totalHits = response.hits().total() != null
                    ? response.hits().total().value() : 0;

            return new PageImpl<>(posts, PageRequest.of(Math.max(0, page - 1), size), totalHits);

        } catch (Exception e) {
            logger.error("Elasticsearch search failed for keyword '{}': {}", keyword, e.getMessage());
            return Page.empty();
        }
    }

    /**
     * 索引文章到ES
     * @param post 文章实体
     */
    public void indexPost(BlogPost post) {
        if (elasticsearchClient == null) {
            return;
        }

        try {
            Map<String, Object> document = new HashMap<>();
            document.put("post-id", post.getPostId());
            document.put("post-title", post.getPostTitle());
            document.put("post-content", post.getPostContentMd());
            document.put("post-url", post.getPostUrl());
            document.put("post-summary", post.getPostSummary());
            document.put("post-date", post.getPostDate());
            // tag 数据未迁移(08-CONTEXT 决策),不再写入 tags 字段

            IndexRequest<Map<String, Object>> request = IndexRequest.of(i -> i
                    .index(INDEX_NAME)
                    .id(String.valueOf(post.getPostId()))
                    .document(document)
            );

            elasticsearchClient.index(request);
            logger.debug("Indexed post {} to Elasticsearch", post.getPostId());

        } catch (Exception e) {
            logger.warn("Failed to index post {} to Elasticsearch: {}", post.getPostId(), e.getMessage());
        }
    }

    /**
     * 从ES删除文章索引
     * @param postId 文章ID
     */
    public void deletePost(Long postId) {
        if (elasticsearchClient == null) {
            return;
        }

        try {
            DeleteRequest request = DeleteRequest.of(d -> d
                    .index(INDEX_NAME)
                    .id(String.valueOf(postId))
            );

            elasticsearchClient.delete(request);
            logger.debug("Deleted post {} from Elasticsearch index", postId);

        } catch (Exception e) {
            logger.warn("Failed to delete post {} from Elasticsearch: {}", postId, e.getMessage());
        }
    }

    /**
     * 将ES文档映射为BlogPost实体
     * 按D-13字段映射: post-id, post-title, post-content, post-url, post-summary, post-date, tags
     */
    private BlogPost mapToPost(Map<String, Object> source) {
        BlogPost post = new BlogPost();

        Object postId = source.get("post-id");
        if (postId instanceof Number) {
            post.setPostId(((Number) postId).longValue());
        }

        post.setPostTitle((String) source.get("post-title"));
        post.setPostContentMd((String) source.get("post-content"));
        post.setPostUrl((String) source.get("post-url"));
        post.setPostSummary((String) source.get("post-summary"));

        Object postDate = source.get("post-date");
        if (postDate instanceof Number) {
            post.setPostDate(new Date(((Number) postDate).longValue()));
        } else if (postDate instanceof String) {
            // ES may store dates as ISO strings
            try {
                post.setPostDate(new Date((String) postDate));
            } catch (Exception ignored) {
                // Date parsing failed, leave null
            }
        }

        return post;
    }
}
