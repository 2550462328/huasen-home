<!--
 * @Autor: huizhang43
 * @Date: 2026-06-09
 * @Description: tiny-blog文章列表页面 (参考blog-sharon现代化设计，无搜索框)
-->
<template>
  <div class="tiny-blog-list">
    <!-- 左侧边栏 -->
    <div class="sidebar">
      <!-- 搜索框 -->
      <div class="search-section">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索文章标题..."
          prefix-icon="el-icon-search"
          clearable
          @keyup.enter.native="handleSearch"
          @clear="clearSearch"
        >
        </el-input>
        <el-button
          type="primary"
          icon="el-icon-search"
          style="width: 100%; margin-top: 10px"
          @click="handleSearch"
        >
          搜索
        </el-button>
      </div>

      <!-- 分类区域 - 占满所有空间 -->
      <div class="category-section">
        <!-- 全部分类卡片 -->
        <div
          class="cat-card"
          :class="{ active: selectedCategoryId === null }"
          @click="selectCategory(null)"
        >
          <span class="cat-icon-avatar" style="background: #5b8def">全</span>
          <span class="cat-name">全部文章</span>
          <span class="cat-count">{{ totalElements }}</span>
        </div>

        <!-- 分类列表 -->
        <div
          v-for="cat in categories"
          :key="cat.id"
          class="cat-card"
          :class="{ active: selectedCategoryId === cat.id }"
          @click="selectCategory(cat.id)"
        >
          <span class="cat-icon-avatar" :style="{ background: getCategoryColor(cat.name) }">
            {{ cat.name.charAt(0).toUpperCase() }}
          </span>
          <span class="cat-name">{{ cat.name }}</span>
          <span v-if="cat.postCount > 0" class="cat-count">{{ cat.postCount }}</span>
        </div>

        <!-- 分类为空 -->
        <div v-if="categories.length === 0" class="category-empty">
          <i class="el-icon-folder-opened"></i>
          <span>暂无分类</span>
        </div>
      </div>
    </div>

    <!-- 右侧内容区 -->
    <div class="main-content">
      <div class="content-wrap">
        <!-- 页头 -->
        <div class="page-header">
          <h1 class="page-title">{{ selectedCategoryName || '全部文章' }}</h1>
          <span class="page-count">共 {{ totalElements }} 篇</span>
        </div>

        <!-- 骨架屏加载 -->
        <div v-if="loading" class="article-list">
          <div v-for="i in 4" :key="i" class="skeleton-item">
            <div class="skeleton-main">
              <div class="skeleton-title"></div>
              <div class="skeleton-line"></div>
              <div class="skeleton-line short"></div>
              <div class="skeleton-footer"></div>
            </div>
            <div class="skeleton-cover"></div>
          </div>
        </div>

        <!-- 文章列表 -->
        <div v-else-if="articles.length > 0" class="article-list">
          <div
            v-for="item in articles"
            :key="item.id"
            class="article-item"
            @click="goToDetail(item.id)"
          >
            <div class="article-main">
              <h3 class="article-title">{{ item.title }}</h3>
              <p class="article-summary">
                {{ item.summary || '暂无摘要' }}
              </p>
              <div class="article-meta">
                <span v-if="item.category" class="meta-tag">
                  {{ item.category.name }}
                </span>
                <span class="meta-item">
                  <i class="el-icon-date"></i>
                  {{ formatDate(item.publishDate) }}
                </span>
                <span v-if="item.author" class="meta-item">
                  <i class="el-icon-user"></i>
                  {{ item.author }}
                </span>
                <span class="meta-item">
                  <i class="el-icon-view"></i>
                  {{ item.visitCount || 0 }} 阅读
                </span>
              </div>
            </div>
            <div v-if="item.coverImage" class="article-cover">
              <img :src="item.coverImage" :alt="item.title" />
            </div>
          </div>
        </div>

        <!-- 空状态 -->
        <div v-else class="empty-state">
          <i class="el-icon-document-delete"></i>
          <p>暂无文章</p>
        </div>

        <!-- 分页 -->
        <div v-if="totalElements > pageSize" class="pagination-wrapper">
          <el-pagination
            background
            layout="prev, pager, next, jumper"
            :total="totalElements"
            :page-size="pageSize"
            :current-page.sync="currentPage"
            @current-change="handlePageChange"
          />
        </div>
      </div>

      <!-- 回到顶部 -->
      <el-backtop target=".main-content" :bottom="80" :right="40">
        <div class="back-to-top">
          <i class="el-icon-caret-top"></i>
        </div>
      </el-backtop>
    </div>
  </div>
</template>

<script>
export default {
  name: 'TinyBlogList',
  data() {
    return {
      articles: [],
      categories: [],
      currentPage: 1,
      totalElements: 0,
      pageSize: 10,
      selectedCategoryId: null,
      selectedCategoryName: '',
      searchKeyword: '',
      loading: false,
      savedScrollTop: 0, // keep-alive 缓存时保存滚动位置
    };
  },
  created() {
    this.fetchCategories();
    this.fetchArticles(1);
  },
  // keep-alive 激活时恢复滚动位置
  activated() {
    this.$nextTick(() => {
      const container = document.querySelector('.main-content');
      if (container && this.savedScrollTop) {
        container.scrollTop = this.savedScrollTop;
      }
    });
  },
  // keep-alive 停用时保存滚动位置
  deactivated() {
    const container = document.querySelector('.main-content');
    if (container) {
      this.savedScrollTop = container.scrollTop;
    }
  },
  methods: {
    fetchCategories() {
      // 缓存键和过期时间（24小时）
      const CACHE_KEY = 'tiny_blog_categories_cache_v1';
      const CACHE_DURATION = 24 * 60 * 60 * 1000; // 24小时

      // 尝试从缓存读取
      try {
        const cached = localStorage.getItem(CACHE_KEY);
        if (cached) {
          const { data, timestamp } = JSON.parse(cached);
          const now = Date.now();
          // 如果缓存未过期，直接使用
          if (now - timestamp < CACHE_DURATION) {
            this.categories = data || [];
            return;
          }
        }
      } catch (e) {
        // 缓存读取失败，忽略
      }

      // 缓存不存在或已过期，从服务器加载
      this.API.getTinyBlogCategories({}, { notify: false })
        .then(res => {
          this.categories = res.data || [];

          // 保存到缓存
          try {
            localStorage.setItem(CACHE_KEY, JSON.stringify({
              data: this.categories,
              timestamp: Date.now()
            }));
          } catch (e) {
            // 缓存写入失败（可能是存储空间满了），忽略
          }
        })
        .catch(() => {
          this.categories = [];
        });
    },

    fetchArticles(page) {
      this.loading = true;
      let params = { size: this.pageSize };
      if (this.searchKeyword.trim()) {
        params.keyword = this.searchKeyword.trim();
      } else if (this.selectedCategoryId) {
        params.categoryId = this.selectedCategoryId;
      }

      this.API.getTinyBlogPostsPage(page, params, { notify: false })
        .then(res => {
          let data = res.data || {};
          this.articles = data.content || [];
          this.totalElements = data.totalElements || 0;
          this.currentPage = data.currentPage || page;
          this.loading = false;
        })
        .catch(() => {
          this.articles = [];
          this.totalElements = 0;
          this.loading = false;
        });
    },

    handleSearch() {
      if (!this.searchKeyword.trim()) {
        return;
      }
      // 搜索时清空分类选择,标题检索优先
      this.selectedCategoryId = null;
      this.selectedCategoryName = '';
      this.currentPage = 1;
      this.fetchArticles(1);
    },

    clearSearch() {
      this.searchKeyword = '';
      this.currentPage = 1;
      this.fetchArticles(1);
    },

    selectCategory(id) {
      this.selectedCategoryId = id;
      this.currentPage = 1;
      this.searchKeyword = '';

      // 设置选中分类名称
      if (id === null) {
        this.selectedCategoryName = '';
      } else {
        const cat = this.categories.find(c => c.id === id);
        this.selectedCategoryName = cat ? cat.name : '';
      }

      this.fetchArticles(1);
    },

    handlePageChange(page) {
      this.currentPage = page;
      this.fetchArticles(page);
      // 滚动到顶部
      document.querySelector('.main-content').scrollTop = 0;
    },

    goToDetail(id) {
      this.$router.push({ path: `/tiny-blog/${id}` });
    },

    formatDate(dateStr) {
      if (!dateStr) return '';
      let d = new Date(dateStr);
      let year = d.getFullYear();
      let month = String(d.getMonth() + 1).padStart(2, '0');
      let day = String(d.getDate()).padStart(2, '0');
      return `${year}-${month}-${day}`;
    },

    getCategoryColor(name) {
      const palette = [
        '#5b8def', '#3aa675', '#d99441', '#d96a6a',
        '#8b7fd4', '#c76fa3', '#3ba3b5', '#c98a52',
      ];
      let hash = 0;
      for (let i = 0; i < name.length; i++) {
        hash = (hash * 31 + name.charCodeAt(i)) >>> 0;
      }
      return palette[hash % palette.length];
    },
  },
};
</script>

<style lang="scss" scoped>
$accent: #1e80ff;
$accent-soft: rgba(30, 128, 255, 0.1);
$ink: #1d2129;
$ink-soft: #8a919f;
$line: #f0f0f2;
$bg: #f4f5f5;
$paper-bg: #faf6f0; // 纸张色（奶油色）
$paper-line: #e6ddcb; // 纸张主题描边色
$brand: #b5853e; // 书卷主题强调色（赭金）
$brand-deep: #8a6328; // 深赭，用于标题
$brand-soft: rgba(181, 133, 62, 0.1); // 浅赭底

.tiny-blog-list {
  display: flex;
  width: 100%;
  height: 100%;
  background: $bg;
}

/* ========== 左侧边栏 ========== */
.sidebar {
  width: 260px;
  height: 100%;
  background: #ffffff;
  border-right: 1px solid #e8e8e8;
  overflow-y: auto;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;

  &::-webkit-scrollbar {
    width: 6px;
  }
  &::-webkit-scrollbar-thumb {
    background: #ddd;
    border-radius: 3px;
  }

  /* 搜索区域 */
  .search-section {
    padding: 20px;
    margin-bottom: 12px; /* 增加与下方分类的间距 */

    ::v-deep .el-input__inner {
      border-radius: 6px;
      background: #fafafa;
      border: 1px solid #e0e0e0;
      transition: all 0.2s;
      font-size: 14px;
      height: 38px;

      &:focus {
        background: #fff;
        border-color: $accent;
      }
    }

    ::v-deep .el-button {
      border-radius: 6px;
      font-weight: 400;
      height: 38px;
    }
  }

  /* 分类区域 - 占满所有空间 */
  .category-section {
    flex: 1;
    overflow-y: auto;
    padding: 0 0 12px; /* 底部留出一些呼吸空间 */

    .category-empty {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 16px;
      padding: 80px 24px;
      color: #ccc;
      font-size: 15px;

      i {
        font-size: 48px;
        opacity: 0.5;
      }
    }
  }
}

/* ========== 分类卡片样式 ========== */
.cat-card {
  position: relative;
  display: flex;
  align-items: center;
  padding: 14px 20px;
  margin: 0 12px 6px; /* 左右边距 + 下边距，增加卡片间隔 */
  border-radius: 8px; /* 圆角让卡片更柔和 */
  cursor: pointer;
  transition: all 0.2s ease;
  user-select: none;

  &:hover {
    background: #f9f9f9;
  }

  &.active {
    background: #e8f4ff; /* 选中态使用品牌色的浅色背景 */
    border-left: 3px solid $accent; /* 左侧添加强调色条 */
    padding-left: 17px; /* 补偿边框占用的空间 */
  }

  /* 分类图标 - 首字母色块 */
  .cat-icon-avatar {
    width: 20px;
    height: 20px;
    border-radius: 4px;
    margin-right: 14px;
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    font-size: 12px;
    font-weight: 600;
    line-height: 1;
  }

  .cat-name {
    flex: 1;
    font-size: 14px;
    font-weight: 400;
    color: #2a2a2a;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    line-height: 1.5;
    letter-spacing: 0.2px;
  }

  /* 文章数量 */
  .cat-count {
    flex-shrink: 0;
    margin-left: 8px;
    font-size: 12px;
    color: #999;
    background: #f5f5f5;
    padding: 2px 8px;
    border-radius: 10px;
  }

  /* 叶子节点右箭头 > */
  &::after {
    content: '\e6e0';
    font-family: element-icons !important;
    font-size: 14px;
    color: #d0d0d0;
    flex-shrink: 0;
    margin-left: 8px;
  }
}

/* ========== 右侧内容区 ========== */
.main-content {
  flex: 1;
  height: 100%;
  overflow-y: auto;
  padding: 24px 24px 60px;
  background: $paper-bg;
  /* 纸张纹理效果 */
  background-image:
    repeating-linear-gradient(
      0deg,
      transparent,
      transparent 2px,
      rgba(0, 0, 0, 0.01) 2px,
      rgba(0, 0, 0, 0.01) 4px
    ),
    repeating-linear-gradient(
      90deg,
      transparent,
      transparent 2px,
      rgba(0, 0, 0, 0.01) 2px,
      rgba(0, 0, 0, 0.01) 4px
    );
  background-size: 100% 100%, 100% 100%;
  background-blend-mode: multiply;

  &::-webkit-scrollbar {
    width: 8px;
  }
  &::-webkit-scrollbar-thumb {
    background: #dcdfe6;
    border-radius: 4px;
  }

  .content-wrap {
    max-width: 820px;
    margin: 0 auto;
  }

  /* 页头 */
  .page-header {
    display: flex;
    align-items: baseline;
    gap: 12px;
    padding: 4px 4px 18px;

    .page-title {
      font-size: 20px;
      font-weight: 700;
      color: $ink;
      margin: 0;
    }
    .page-count {
      font-size: 13px;
      color: $ink-soft;
    }
  }

  /* 文章列表 — 卡片容器 */
  .article-list {
    background: #ffffff;
    border-radius: 8px;
    overflow: hidden;
  }

  /* 文章条目 */
  .article-item {
    display: flex;
    align-items: center;
    gap: 20px;
    padding: 20px;
    border-bottom: 1px solid $line;
    cursor: pointer;
    transition: background 0.15s;

    &:last-child {
      border-bottom: none;
    }

    &:hover {
      background: #fafbfc;

      .article-title {
        color: $accent;
      }
      .article-cover img {
        transform: scale(1.05);
      }
    }

    .article-main {
      flex: 1;
      min-width: 0;
      display: flex;
      flex-direction: column;
    }

    .article-title {
      font-size: 16px;
      font-weight: 600;
      color: $ink;
      margin: 0 0 8px 0;
      transition: color 0.2s;
      line-height: 1.5;
      overflow: hidden;
      text-overflow: ellipsis;
      display: -webkit-box;
      -webkit-line-clamp: 1;
      -webkit-box-orient: vertical;
    }

    .article-summary {
      font-size: 13px;
      color: $ink-soft;
      line-height: 1.6;
      margin: 0 0 14px 0;
      overflow: hidden;
      text-overflow: ellipsis;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
    }

    .article-meta {
      display: flex;
      gap: 16px;
      align-items: center;
      font-size: 12px;
      color: #a3a8b3;

      .meta-tag {
        background: #f7f8fa;
        padding: 3px 10px;
        border-radius: 6px;
        color: #6b7280;
        font-weight: 500;
      }

      .meta-item {
        display: flex;
        align-items: center;
        gap: 4px;

        i {
          font-size: 13px;
        }
      }
    }

    .article-cover {
      width: 120px;
      height: 80px;
      flex-shrink: 0;
      border-radius: 6px;
      overflow: hidden;
      background: #f2f3f5;

      img {
        width: 100%;
        height: 100%;
        object-fit: cover;
        transition: transform 0.4s ease;
      }
    }
  }
}

/* ========== 骨架屏 ========== */
.skeleton-item {
  display: flex;
  gap: 20px;
  padding: 20px;
  border-bottom: 1px solid $line;

  .skeleton-main {
    flex: 1;
  }

  .skeleton-title,
  .skeleton-line,
  .skeleton-footer,
  .skeleton-cover {
    background: linear-gradient(90deg, #f2f3f5 25%, #e9eaed 50%, #f2f3f5 75%);
    background-size: 200% 100%;
    animation: skeleton-loading 1.5s ease-in-out infinite;
    border-radius: 4px;
  }

  .skeleton-title {
    height: 20px;
    width: 55%;
    margin-bottom: 14px;
  }

  .skeleton-line {
    height: 13px;
    margin-bottom: 10px;

    &.short {
      width: 75%;
    }
  }

  .skeleton-footer {
    height: 14px;
    width: 35%;
    margin-top: 14px;
  }

  .skeleton-cover {
    width: 120px;
    height: 80px;
    flex-shrink: 0;
    border-radius: 6px;
  }
}

@keyframes skeleton-loading {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}

/* ========== 空状态 ========== */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px;
  background: #fff;
  border-radius: 8px;
  color: $ink-soft;

  i {
    font-size: 60px;
    margin-bottom: 16px;
    opacity: 0.4;
  }

  p {
    font-size: 15px;
    margin: 0;
  }
}

/* ========== 分页 ========== */
.pagination-wrapper {
  display: flex;
  justify-content: center;
  padding: 28px 0 0;
}

/* ========== 回到顶部 ========== */
.back-to-top {
  width: 42px;
  height: 42px;
  background: #ffffff;
  color: $accent;
  border: 1px solid $line;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  transition: all 0.25s;

  &:hover {
    background: $accent;
    color: #fff;
    border-color: $accent;
    transform: translateY(-2px);
  }

  i {
    font-size: 20px;
  }
}

/* ========== 响应式 ========== */
@media (max-width: 768px) {
  .tiny-blog-list {
    flex-direction: column;
  }

  .sidebar {
    width: 100%;
    height: auto;
    border-right: none;
    border-bottom: 1px solid $line;
  }

  .main-content {
    padding: 16px;

    .article-item {
      .article-cover {
        width: 90px;
        height: 64px;
      }
    }
  }
}
</style>
