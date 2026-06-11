package com.huasen.blog.sharon.dto;

import com.huasen.blog.sharon.entity.BlogPost;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 归档响应DTO
 * 用于按年/年月分组展示文章归档
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArchiveDTO {

    /** 年份 */
    private String year;

    /** 月份(年月归档时使用,年归档时为null) */
    private String month;

    /** 该时间段内文章数量 */
    private Long count;

    /** 该时间段内的文章列表 */
    private List<BlogPost> posts;
}
