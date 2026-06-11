/*
 * @Autor: huizhang43
 * @Date: 2026-06-01
 * @Description: tiny-blog接口地址
 */

import { get } from '../request.js';
import { http } from '../http.js';

const getTinyBlogCategories = get('/tiny-blog/categories');

// 分页查询文章列表 - 动态URL需要直接使用http
function getTinyBlogPostsPage(page, params, option = {}) {
  return http({
    url: `/tiny-blog/posts/page/${page}`,
    params,
    ...option,
    method: 'get',
  });
}

function getTinyBlogPostById(id, params, option = {}) {
  return http({
    url: `/tiny-blog/posts/${id}`,
    params,
    ...option,
    method: 'get',
  });
}

export default {
  getTinyBlogCategories,
  getTinyBlogPostsPage,
  getTinyBlogPostById,
};
