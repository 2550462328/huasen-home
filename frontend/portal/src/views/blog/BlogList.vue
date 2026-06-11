<!--
 * @Autor: huizhang43
 * @Date: 2026-06-02
 * @Description: blog-sharon文章列表页面 (掘金风格)
-->
<template>
  <div class="blog-list">
    <!-- 左侧边栏 -->
    <div class="sidebar">
      <!-- 搜索框 -->
      <div class="search-section">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索文章标题..."
          :disabled="!esAvailable"
          prefix-icon="el-icon-search"
          clearable
          @keyup.enter.native="handleSearch"
          @clear="clearSearch"
        >
        </el-input>
        <el-button
          type="primary"
          icon="el-icon-search"
          :disabled="!esAvailable"
          style="width: 100%; margin-top: 10px"
          @click="handleSearch"
        >
          搜索
        </el-button>
        <div v-if="!esAvailable" class="es-tip">
          <i class="el-icon-warning"></i> 搜索服务暂不可用
        </div>
      </div>

      <!-- 分类树 - 占据剩余所有空间 -->
      <div class="category-section">
        <div class="category-tree">
          <category-tree-node
            v-for="node in categoryTree"
            :key="node.id"
            :node="node"
            :selected-id="selectedCategoryId"
            @select="selectCategory"
          />
          <!-- 分类为空 -->
          <div v-if="categoryTree.length === 0" class="category-empty">
            <i class="el-icon-folder-opened"></i>
            <span>暂无分类</span>
          </div>
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
// 递归分类树节点组件
const CategoryTreeNode = {
  name: 'CategoryTreeNode',
  props: {
    node: {
      type: Object,
      required: true,
    },
    selectedId: {
      type: Number,
      default: null,
    },
    depth: {
      type: Number,
      default: 0,
    },
  },
  data() {
    return {
      // 整棵树已预加载, 默认全部折叠, 首次仅展示一级节点
      expanded: false,
      iconError: false, // 图标加载失败时回退到首字色块
    };
  },
  computed: {
    hasChildren() {
      // 整棵树已一次性加载, children 始终是数组
      return Array.isArray(this.node.children) && this.node.children.length > 0;
    },
    // 一级分类(depth=0)样式更重，子分类轻量缩进
    isTop() {
      return this.depth === 0;
    },
    // 有可用图标 URL 且未加载失败
    showIcon() {
      return this.node.icon && !this.iconError;
    },
    avatarText() {
      const name = (this.node.name || '').trim();
      return name ? name.charAt(0).toUpperCase() : '#';
    },
    avatarColor() {
      const palette = [
        '#5b8def', '#3aa675', '#d99441', '#d96a6a',
        '#8b7fd4', '#c76fa3', '#3ba3b5', '#c98a52',
      ];
      const name = this.node.name || '';
      let hash = 0;
      for (let i = 0; i < name.length; i++) {
        hash = (hash * 31 + name.charCodeAt(i)) >>> 0;
      }
      return palette[hash % palette.length];
    },
  },
  methods: {
    onIconError() {
      this.iconError = true;
    },
    toggleExpand() {
      this.expanded = !this.expanded;
    },
    handleSelect(id) {
      this.$emit('select', id);
    },
  },
  template: `
    <div class="tree-item" :class="{ 'is-top': isTop }">
      <!-- 一级分类：卡片式（图标 + 名称 + 简介 + 文章数） -->
      <div
        v-if="isTop"
        class="cat-card"
        :class="{ active: selectedId === node.id }"
        @click="handleSelect(node.id)"
      >
        <img
          v-if="showIcon"
          :src="node.icon"
          class="cat-icon-img"
          alt=""
          @error="onIconError"
        />
        <span
          v-else
          class="cat-icon-avatar"
          :style="{ background: avatarColor }"
        >{{ avatarText }}</span>
        <span class="cat-name">{{ node.name }}</span>
        <span
          v-if="hasChildren"
          class="cat-caret"
          :class="{ open: expanded }"
          @click.stop="toggleExpand"
        >
          <i class="el-icon-caret-bottom"></i>
        </span>
      </div>

      <!-- 子分类：缩进列表 -->
      <div
        v-else
        class="tree-node"
        :class="{ active: selectedId === node.id }"
        @click.stop="handleSelect(node.id)"
      >
        <img
          v-if="showIcon"
          :src="node.icon"
          class="tree-icon-img"
          alt=""
          @error="onIconError"
        />
        <i v-else class="el-icon-document tree-icon"></i>
        <span class="tree-label">{{ node.name }}</span>
        <span
          v-if="hasChildren"
          class="tree-caret"
          :class="{ open: expanded }"
          @click.stop="toggleExpand"
        >
          <i class="el-icon-caret-right"></i>
        </span>
      </div>

      <transition name="tree-expand">
        <div v-if="expanded && hasChildren" class="tree-children" :class="{ 'top-children': isTop }">
          <category-tree-node
            v-for="child in node.children"
            :key="child.id"
            :node="child"
            :depth="depth + 1"
            :selected-id="selectedId"
            @select="handleSelect"
          />
        </div>
      </transition>
    </div>
  `,
};

export default {
  name: 'BlogList',
  components: {
    CategoryTreeNode,
  },
  data() {
    return {
      articles: [],
      categories: [],
      categoryTree: [],
      categoryMap: new Map(), // 用于快速查找节点，支持懒加载更新
      currentPage: 1,
      totalElements: 0,
      pageSize: 10,
      selectedCategoryId: null,
      selectedCategoryName: '',
      searchKeyword: '',
      esAvailable: true,
      loading: false,
      savedScrollTop: 0, // keep-alive 缓存时保存滚动位置
    };
  },
  created() {
    this.fetchCategories();
    this.checkEsAvailability();
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
      // 注意: v3 起一次性拉取全量分类并在前端构建整棵树, 升级 key 丢弃旧结构缓存
      const CACHE_KEY = 'blog_sharon_categories_cache_v3';
      const CACHE_DURATION = 24 * 60 * 60 * 1000; // 24小时

      // 尝试从缓存读取
      try {
        const cached = localStorage.getItem(CACHE_KEY);
        if (cached) {
          const { data, timestamp } = JSON.parse(cached);
          const now = Date.now();
          // 如果缓存未过期，直接使用
          if (now - timestamp < CACHE_DURATION) {
            this.loadCategoriesFromData(data);
            return;
          }
        }
      } catch (e) {
        // 缓存读取失败，忽略
      }

      // 缓存不存在或已过期，从服务器一次性加载全部分类
      this.API.getBlogSharonCategoryTree({}, { notify: false })
        .then(res => {
          const allCategories = res.data || [];
          this.loadCategoriesFromData(allCategories);

          // 保存到缓存
          try {
            localStorage.setItem(CACHE_KEY, JSON.stringify({
              data: allCategories,
              timestamp: Date.now()
            }));
          } catch (e) {
            // 缓存写入失败（可能是存储空间满了），忽略
          }
        })
        .catch(() => {
          this.categories = [];
          this.categoryTree = [];
          this.categoryMap.clear();
        });
    },

    loadCategoriesFromData(allCategories) {
      // allCategories 为扁平全量列表, 在前端组装成父子树结构
      this.categories = allCategories;
      this.categoryMap.clear();

      // 第一遍: 建立 id -> 节点 映射, children 初始化为空数组(全量已知, 无需懒加载)
      const nodeMap = new Map();
      allCategories.forEach(cat => {
        nodeMap.set(cat.id, {
          id: cat.id,
          name: cat.name,
          url: cat.url,
          icon: cat.icon || '',
          desc: cat.desc || '',
          parentId: cat.parentId,
          hasChildren: cat.hasChildren || false,
          count: cat.count || 0,
          children: [],
        });
      });

      // 第二遍: 挂载到父节点, 同时收集根节点
      const roots = [];
      allCategories.forEach(cat => {
        const node = nodeMap.get(cat.id);
        const parent = (cat.parentId && cat.parentId !== 0) ? nodeMap.get(cat.parentId) : null;
        if (parent) {
          parent.children.push(node);
        } else {
          roots.push(node);
        }
      });

      this.categoryTree = roots;
      nodeMap.forEach((node, id) => this.categoryMap.set(id, node));
    },

    checkEsAvailability() {
      this.API.searchBlogSharonPosts({ keyword: '', size: 1 }, { notify: false })
        .then(() => {
          this.esAvailable = true;
        })
        .catch(() => {
          this.esAvailable = false;
        });
    },

    fetchArticles(page) {
      this.loading = true;
      let params = { size: this.pageSize };
      if (this.selectedCategoryId) {
        params.categoryId = this.selectedCategoryId;
      }

      this.API.getBlogSharonPostsPage(page, params, { notify: false })
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
      if (!this.esAvailable || !this.searchKeyword.trim()) {
        return;
      }
      this.loading = true;
      this.API.searchBlogSharonPosts(
        { keyword: this.searchKeyword, page: 1, size: this.pageSize },
        { notify: false }
      )
        .then(res => {
          let data = res.data || {};
          this.articles = data.content || [];
          this.totalElements = data.totalElements || 0;
          this.currentPage = 1;
          this.selectedCategoryId = null;
          this.selectedCategoryName = '';
          this.loading = false;
        })
        .catch(() => {
          this.esAvailable = false;
          this.loading = false;
        });
    },

    clearSearch() {
      this.searchKeyword = '';
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
      this.$router.push({ path: `/blog/${id}` });
    },

    formatDate(dateStr) {
      if (!dateStr) return '';
      let d = new Date(dateStr);
      let year = d.getFullYear();
      let month = String(d.getMonth() + 1).padStart(2, '0');
      let day = String(d.getDate()).padStart(2, '0');
      return `${year}-${month}-${day}`;
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
$paper-bg: #faf6f0; // 更真实的纸张色（奶油色）
$paper-line: #e6ddcb; // 纸张主题描边色
$brand: #b5853e; // 书卷主题强调色（赭金）
$brand-deep: #8a6328; // 深赭，用于标题
$brand-soft: rgba(181, 133, 62, 0.1); // 浅赭底

.blog-list {
  display: flex;
  width: 100%;
  height: 100%;
  background: $bg;
}

/* ========== 左侧边栏 ========== */
.sidebar {
  width: 260px; /* 缩小 */
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

    .es-tip {
      margin-top: 10px;
      font-size: 12px;
      color: #999;
      display: flex;
      align-items: center;
      gap: 6px;
    }
  }

  /* 分类区域 - 占满剩余空间 */
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

/* ========== 分类树样式（参考图风格 - 大气舒展） ========== */
.category-tree {
  ::v-deep .tree-item {
    margin-bottom: 0;
  }

  /* ===== 一级分类 ===== */
  ::v-deep .cat-card {
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

    .cat-icon {
      font-size: 17px; /* 缩小 */
      color: #555;
      margin-right: 14px; /* 缩小 */
      flex-shrink: 0;
    }

    /* 分类自定义图标（图片 URL） */
    .cat-icon-img {
      width: 20px;
      height: 20px;
      object-fit: cover;
      border-radius: 4px;
      margin-right: 14px;
      flex-shrink: 0;
    }

    /* 图标缺失/加载失败时的首字母色块回退 */
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
      font-size: 14px; /* 缩小 */
      font-weight: 400;
      color: #2a2a2a;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      line-height: 1.5;
      letter-spacing: 0.2px;
    }

    /* 展开/收起箭头 ▼（在右侧） */
    .cat-caret {
      flex-shrink: 0;
      width: 20px;
      height: 20px;
      display: flex;
      align-items: center;
      justify-content: center;
      color: #999;
      transition: transform 0.25s ease;

      &.open {
        transform: rotate(180deg);
      }

      i {
        font-size: 13px;
      }
    }

    /* 叶子节点右箭头 > */
    &::after {
      content: '\e6e0';
      font-family: element-icons !important;
      font-size: 14px;
      color: #d0d0d0;
      flex-shrink: 0;
    }

    /* 有展开按钮时隐藏右箭头 */
    &:has(.cat-caret)::after {
      display: none;
    }
  }

  /* ===== 子分类 ===== */
  ::v-deep .tree-node {
    position: relative;
    display: flex;
    align-items: center;
    padding: 12px 20px 12px 52px;
    margin: 0 12px 4px; /* 与父级卡片一致的左右边距 */
    border-radius: 6px; /* 子分类圆角稍小 */
    cursor: pointer;
    transition: all 0.2s ease;
    user-select: none;

    &:hover {
      background: #f9f9f9;
    }

    &.active {
      background: #e8f4ff;
      border-left: 3px solid $accent;
      padding-left: 49px; /* 补偿边框 */
    }

    .tree-icon {
      font-size: 15px; /* 缩小 */
      color: #888;
      margin-right: 14px; /* 缩小 */
      flex-shrink: 0;
    }

    /* 子分类自定义图标（图片 URL） */
    .tree-icon-img {
      width: 18px;
      height: 18px;
      object-fit: cover;
      border-radius: 4px;
      margin-right: 14px;
      flex-shrink: 0;
    }

    .tree-label {
      flex: 1;
      font-size: 14px; /* 缩小 */
      font-weight: 400;
      color: #2a2a2a;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      line-height: 1.5;
      letter-spacing: 0.2px;
    }

    /* 子项展开箭头 >（在右侧，展开时旋转90度） */
    .tree-caret {
      flex-shrink: 0;
      width: 20px;
      height: 20px;
      display: flex;
      align-items: center;
      justify-content: center;
      color: #999;
      transition: transform 0.25s ease;

      &.open {
        transform: rotate(90deg);
      }

      i {
        font-size: 13px;
      }
    }

    /* 叶子节点右箭头 > */
    &::after {
      content: '\e6e0';
      font-family: element-icons !important;
      font-size: 14px;
      color: #d0d0d0;
      flex-shrink: 0;
    }

    /* 有展开按钮时隐藏右箭头 */
    &:has(.tree-caret)::after {
      display: none;
    }
  }

  /* 子级容器 - 缩进 */
  ::v-deep .tree-children {
    border-left: none;
    margin-left: 0;
    padding-left: 0;

    .tree-node {
      padding-left: 68px; /* 缩小 */
    }

    &.top-children {
      margin: 0;
      padding-bottom: 0;
    }

    /* 三级缩进 */
    .tree-children .tree-node {
      padding-left: 84px;
    }
  }
}

/* 展开动画 */
.tree-expand-enter-active,
.tree-expand-leave-active {
  transition: all 0.2s ease;
  overflow: hidden;
}
.tree-expand-enter,
.tree-expand-leave-to {
  opacity: 0;
  max-height: 0;
}
.tree-expand-enter-to,
.tree-expand-leave {
  max-height: 600px;
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
  .blog-list {
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
