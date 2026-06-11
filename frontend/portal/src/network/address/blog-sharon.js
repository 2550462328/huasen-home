/*
 * @Autor: huizhang43
 * @Date: 2026-06-02
 * @Description: blog-sharon接口地址
 */

import { get } from '../request.js';
import { http } from '../http.js';

const getBlogSharonCategories = get('/blog-sharon/categories');

// 一次性获取全部分类(扁平全量),前端自行构建分类树
const getBlogSharonCategoryTree = get('/blog-sharon/categories/tree');

// 懒加载子分类
function getBlogSharonCategoryChildren(parentId, params, option = {}) {
  return http({
    url: `/blog-sharon/categories/${parentId}/children`,
    params,
    ...option,
    method: 'get',
  });
}

const getBlogSharonTags = get('/blog-sharon/tags');

// 分页查询文章列表 - 动态URL需要直接使用http
function getBlogSharonPostsPage(page, params, option = {}) {
  let tab = (params && params.tab != null) ? params.tab : 1;
  return http({
    url: `/blog-sharon/posts/page/${page}/${tab}`,
    params,
    ...option,
    method: 'get',
  });
}

function getBlogSharonPostById(id, params, option = {}) {
  return http({
    url: `/blog-sharon/posts/${id}`,
    params,
    ...option,
    method: 'get',
  });
}

function searchBlogSharonPosts(params, option = {}) {
  return http({
    url: `/blog-sharon/search`,
    params,
    ...option,
    method: 'get',
  });
}

export default {
  getBlogSharonCategories,
  getBlogSharonCategoryTree,
  getBlogSharonCategoryChildren,
  getBlogSharonTags,
  getBlogSharonPostsPage,
  getBlogSharonPostById,
  searchBlogSharonPosts,
};
