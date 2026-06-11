<!--
 * @Autor: huizhang43
 * @Date: 2026-06-01
 * @Description: blog-sharon文章管理
-->
<template>
  <div class="blog-sharon-manage">
    <div class="toolbar">
      <el-input
        v-model="searchKeyword"
        prefix-icon="el-icon-search"
        placeholder="搜索文章标题..."
        style="width: 240px"
        clearable
        @clear="fetchArticles"
        @keyup.enter.native="searchArticles"
      ></el-input>
      <el-select v-model="statusFilter" placeholder="全部状态" style="width: 120px; margin-left: 12px" @change="handleStatusChange">
        <el-option label="全部" :value="null"></el-option>
        <el-option label="已发布" :value="0"></el-option>
        <el-option label="草稿" :value="1"></el-option>
        <el-option label="回收站" :value="2"></el-option>
      </el-select>
    </div>

    <el-table :data="articles" v-loading="loading" style="width: 100%">
      <el-table-column prop="postTitle" label="标题" min-width="200" show-overflow-tooltip></el-table-column>
      <el-table-column label="分类" width="120">
        <template slot-scope="scope">
          {{ formatCategory(scope.row.categories) }}
        </template>
      </el-table-column>
      <el-table-column label="标签" width="150">
        <template slot-scope="scope">
          {{ formatTags(scope.row.tags) }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template slot-scope="scope">
          <el-tag v-if="scope.row.postStatus === 0" type="success" size="small">已发布</el-tag>
          <el-tag v-else-if="scope.row.postStatus === 1" type="info" size="small">草稿</el-tag>
          <el-tag v-else-if="scope.row.postStatus === 2" type="danger" size="small">回收站</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="发布日期" width="120">
        <template slot-scope="scope">
          {{ formatDate(scope.row.postDate) }}
        </template>
      </el-table-column>
      <el-table-column prop="postViews" label="访问量" width="80"></el-table-column>
      <el-table-column label="操作" width="150">
        <template slot-scope="scope">
          <el-tooltip content="已归档的博客禁止编辑" placement="top" :disabled="false">
            <span>
              <el-button type="text" disabled style="cursor: not-allowed">编辑</el-button>
            </span>
          </el-tooltip>
          <el-button type="text" style="color: #f56c6c" @click="deleteArticle(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination
        background
        layout="total, prev, pager, next"
        :total="total"
        :page-size="pageSize"
        :current-page.sync="currentPage"
        @current-change="handlePageChange"
      ></el-pagination>
    </div>
  </div>
</template>

<script>
import axios from '@/network/blogSharonRequest.js';

export default {
  name: 'BlogSharonManage',
  data() {
    return {
      articles: [],
      currentPage: 1,
      pageSize: 15,
      total: 0,
      searchKeyword: '',
      statusFilter: null,
      loading: false,
    };
  },
  created() {
    this.fetchArticles();
  },
  methods: {
    fetchArticles() {
      this.loading = true;
      axios
        .get(`/blog-sharon/admin/posts`, {
          params: { page: this.currentPage, size: this.pageSize, status: this.statusFilter },
        })
        .then(res => {
          if (res.data && res.data.data) {
            let data = res.data.data;
            this.articles = data.list || [];
            this.total = data.total || 0;
          }
        })
        .catch(() => {
          this.$message.error('文章加载失败，请刷新页面重试');
        })
        .finally(() => {
          this.loading = false;
        });
    },

    searchArticles() {
      if (!this.searchKeyword.trim()) {
        this.fetchArticles();
        return;
      }
      this.loading = true;
      this.currentPage = 1;
      axios
        .get(`/blog-sharon/admin/posts/search`, {
          params: { keyword: this.searchKeyword, status: this.statusFilter, page: this.currentPage, size: this.pageSize },
        })
        .then(res => {
          if (res.data && res.data.data) {
            let data = res.data.data;
            this.articles = data.list || [];
            this.total = data.total || 0;
          }
        })
        .catch(() => {
          this.$message.error('搜索失败，请重试');
        })
        .finally(() => {
          this.loading = false;
        });
    },

    deleteArticle(row) {
      this.$confirm(`确定要删除文章「${row.postTitle}」吗？删除后无法恢复。`, '删除文章', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      })
        .then(() => {
          axios
            .delete(`/blog-sharon/admin/posts/${row.postId}`)
            .then(() => {
              this.$message.success('删除成功');
              this.fetchArticles();
            })
            .catch(() => {
              this.$message.error('删除失败，请重试');
            });
        })
        .catch(() => {});
    },

    goToEditor(id) {
      if (id) {
        this.$router.push({ path: '/blog-sharon-editor', query: { id } });
      } else {
        this.$router.push({ path: '/blog-sharon-editor' });
      }
    },

    handlePageChange(page) {
      this.currentPage = page;
      if (this.searchKeyword.trim()) {
        this.searchArticles();
      } else {
        this.fetchArticles();
      }
    },

    handleStatusChange() {
      this.currentPage = 1;
      if (this.searchKeyword.trim()) {
        this.searchArticles();
      } else {
        this.fetchArticles();
      }
    },

    formatDate(dateStr) {
      if (!dateStr) return '-';
      return dateStr.substring(0, 10);
    },

    formatCategory(categories) {
      if (!categories || categories.length === 0) return '-';
      return categories[0].cateName || '-';
    },

    formatTags(tags) {
      // tags 数据未迁移,暂时显示 "-"
      return '-';
    },
  },
};
</script>

<style lang="scss" scoped>
.blog-sharon-manage {
  width: 100%;
  height: calc(100% - 120px);
  padding: 10px;

  .toolbar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;

    .toolbar-left {
      display: flex;
      align-items: center;
    }
  }

  .pagination-wrap {
    display: flex;
    justify-content: flex-end;
    margin-top: 16px;
  }
}
</style>
