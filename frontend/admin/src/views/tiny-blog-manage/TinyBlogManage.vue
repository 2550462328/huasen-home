<!--
 * @Autor: huizhang43
 * @Date: 2026-06-01
 * @Description: tiny-blog文章管理
-->
<template>
  <div class="tiny-blog-manage">
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
      <el-button type="primary" icon="el-icon-upload2" @click="triggerImport">导入文章</el-button>
      <input
        ref="mdFileInput"
        type="file"
        accept=".md,.markdown,text/markdown"
        style="display: none"
        @change="handleFileImport"
      />
    </div>

    <el-table :data="articles" v-loading="loading" style="width: 100%">
      <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip></el-table-column>
      <el-table-column label="分类" width="120">
        <template slot-scope="scope">
          {{ scope.row.category ? scope.row.category.name : '-' }}
        </template>
      </el-table-column>
      <el-table-column label="发布日期" width="120">
        <template slot-scope="scope">
          {{ formatDate(scope.row.publishDate) }}
        </template>
      </el-table-column>
      <el-table-column prop="visitCount" label="访问量" width="80"></el-table-column>
      <el-table-column label="操作" width="150">
        <template slot-scope="scope">
          <el-button type="text" @click="goToEditor(scope.row.id)">编辑</el-button>
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
  name: 'TinyBlogManage',
  data() {
    return {
      articles: [],
      currentPage: 1,
      pageSize: 15,
      total: 0,
      searchKeyword: '',
      loading: false,
    };
  },
  created() {
    this.fetchArticles();
  },
  activated() {
    // 来自表盘"导入文章"快捷入口：自动唤起文件选择
    // 动作经 store 传递（非路由 query），避免 WrapRight 的 tab 系统因 query 变化销毁本组件
    if (this.$store.state.pendingAction === 'import') {
      this.$store.commit('commitAll', { pendingAction: '' });
      this.$nextTick(() => {
        this.triggerImport();
      });
    }
  },
  methods: {
    fetchArticles() {
      this.loading = true;
      axios
        .get(`/tiny-blog/admin/posts`, {
          params: { page: this.currentPage, size: this.pageSize },
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
        .get(`/tiny-blog/admin/posts/search`, {
          params: { keyword: this.searchKeyword, page: this.currentPage, size: this.pageSize },
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
      this.$confirm(`确定要删除「${row.title}」吗？此操作不可恢复`, '删除文章', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      })
        .then(() => {
          axios
            .delete(`/tiny-blog/admin/posts/${row.id}`)
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
        this.$router.push({ path: '/tiny-blog-editor', query: { id } });
      } else {
        this.$router.push({ path: '/tiny-blog-editor' });
      }
    },

    triggerImport() {
      this.$refs.mdFileInput.value = '';
      this.$refs.mdFileInput.click();
    },

    handleFileImport(event) {
      const file = event.target.files && event.target.files[0];
      if (!file) return;

      const lowerName = (file.name || '').toLowerCase();
      if (!/\.(md|markdown)$/.test(lowerName)) {
        this.$message.error('仅支持 Markdown(.md) 文件导入');
        return;
      }
      // 限制文件大小，避免超大文件卡死编辑器（5MB）
      if (file.size > 5 * 1024 * 1024) {
        this.$message.error('文件过大，请导入 5MB 以内的 Markdown 文件');
        return;
      }

      const reader = new FileReader();
      reader.onload = e => {
        try {
          const raw = e.target.result || '';
          const parsed = this.parseMarkdown(raw, file.name);
          // 暂存到 sessionStorage，跳转编辑器预填后由人工选分类/封面再保存
          sessionStorage.setItem('tinyBlogImportDraft', JSON.stringify(parsed));
          this.$router.push({ path: '/tiny-blog-editor', query: { import: '1' } });
        } catch (err) {
          this.$message.error('文件解析失败，请检查 Markdown 格式');
        }
      };
      reader.onerror = () => {
        this.$message.error('文件读取失败，请重试');
      };
      reader.readAsText(file, 'utf-8');
    },

    /**
     * 解析 Markdown 文件，支持可选的 YAML front-matter
     * 提取 title / summary / author / publishDate 元信息，其余作为正文
     */
    parseMarkdown(raw, fileName) {
      let content = raw.replace(/^\uFEFF/, ''); // 去除 BOM
      const meta = { title: '', summary: '', author: '', publishDate: '' };

      // 解析 front-matter（--- 包裹的 key: value）
      const fmMatch = content.match(/^---\s*\r?\n([\s\S]*?)\r?\n---\s*\r?\n?/);
      if (fmMatch) {
        const fmBody = fmMatch[1];
        content = content.slice(fmMatch[0].length);
        fmBody.split(/\r?\n/).forEach(line => {
          const idx = line.indexOf(':');
          if (idx === -1) return;
          const key = line.slice(0, idx).trim().toLowerCase();
          let val = line.slice(idx + 1).trim().replace(/^['"]|['"]$/g, '');
          if (key === 'title') meta.title = val;
          else if (key === 'summary' || key === 'description') meta.summary = val;
          else if (key === 'author') meta.author = val;
          else if (key === 'date' || key === 'publishdate') meta.publishDate = val.substring(0, 10);
        });
      }

      // 无 front-matter title 时，取首个一级标题作为标题
      if (!meta.title) {
        const h1 = content.match(/^\s*#\s+(.+?)\s*$/m);
        if (h1) {
          meta.title = h1[1].trim();
        } else {
          // 兜底：用文件名（去扩展名）
          meta.title = fileName.replace(/\.(md|markdown)$/i, '');
        }
      }

      meta.content = content.trim();
      return meta;
    },

    handlePageChange(page) {
      this.currentPage = page;
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
  },
};
</script>

<style lang="scss" scoped>
.tiny-blog-manage {
  width: 100%;
  height: calc(100% - 120px);
  padding: 10px;

  .toolbar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;
  }

  .pagination-wrap {
    display: flex;
    justify-content: flex-end;
    margin-top: 16px;
  }
}
</style>
