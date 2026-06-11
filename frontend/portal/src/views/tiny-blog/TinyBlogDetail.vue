<!--
 * @Autor: huizhang43
 * @Date: 2026-06-09
 * @Description: tiny-blog文章详情页面 - Markdown渲染 + TOC导航 (参考blog-sharon现代化设计)
-->
<template>
  <div class="tiny-blog-detail">
    <!-- 加载状态 -->
    <div v-if="loading" class="state-wrap">加载中...</div>

    <!-- 错误状态 -->
    <div v-else-if="error" class="state-wrap error-state">
      <p>文章不存在或已被删除</p>
      <router-link to="/tiny-blog" class="back-btn">← 返回文章列表</router-link>
    </div>

    <!-- 文章主体 -->
    <div v-else class="detail-layout">
      <main class="article-card">
        <router-link to="/tiny-blog" class="back-link">← 返回文章列表</router-link>
        <header class="article-header">
          <h1 class="article-title">{{ article.title }}</h1>
          <div class="article-meta">
            <span v-if="article.category" class="meta-tag">{{ article.category.name }}</span>
            <span class="meta-item"><i class="el-icon-date"></i>{{ formatDate(article.publishDate) }}</span>
            <span v-if="article.author" class="meta-item"><i class="el-icon-user"></i>{{ article.author }}</span>
            <span class="meta-item"><i class="el-icon-view"></i>{{ article.visitCount || 0 }} 阅读</span>
          </div>
        </header>

        <!-- 内容区域 -->
        <article class="content-area" ref="contentArea" v-html="htmlContent"></article>
      </main>

      <!-- 右侧栏：目录 -->
      <aside v-if="tocItems.length > 0" class="side-rail">
        <div class="toc-card">
          <div class="toc-head">目录</div>
          <nav class="toc-nav">
            <a
              v-for="item in tocItems"
              :key="item.id"
              :href="'#' + item.id"
              class="toc-item"
              :class="['toc-level-' + item.level, { active: activeHeadingId === item.id }]"
              @click.prevent="scrollToHeading(item.id)"
            >
              {{ item.text }}
            </a>
          </nav>
        </div>
      </aside>
    </div>
  </div>
</template>

<script>
import showdown from 'showdown';
import hljs from 'highlight.js/lib/core';
import javascript from 'highlight.js/lib/languages/javascript';
import java from 'highlight.js/lib/languages/java';
import python from 'highlight.js/lib/languages/python';
import go from 'highlight.js/lib/languages/go';
import sql from 'highlight.js/lib/languages/sql';
import bash from 'highlight.js/lib/languages/bash';
import xml from 'highlight.js/lib/languages/xml';
import 'highlight.js/styles/github.css';

hljs.registerLanguage('javascript', javascript);
hljs.registerLanguage('java', java);
hljs.registerLanguage('python', python);
hljs.registerLanguage('go', go);
hljs.registerLanguage('sql', sql);
hljs.registerLanguage('bash', bash);
hljs.registerLanguage('xml', xml);

export default {
  name: 'TinyBlogDetail',
  data() {
    return {
      article: null,
      htmlContent: '',
      tocItems: [],
      activeHeadingId: '',
      loading: true,
      error: false,
    };
  },
  created() {
    this.fetchArticle();
  },
  mounted() {
    window.addEventListener('scroll', this.handleScroll, true);
  },
  beforeDestroy() {
    window.removeEventListener('scroll', this.handleScroll, true);
  },
  methods: {
    fetchArticle() {
      let id = this.$route.params.id;
      if (!id) {
        this.error = true;
        this.loading = false;
        return;
      }
      this.API.getTinyBlogPostById(id, {}, { notify: false })
        .then(res => {
          if (!res.data) {
            this.error = true;
            this.loading = false;
            return;
          }
          this.article = res.data;
          this.renderMarkdown();
          this.loading = false;
        })
        .catch(() => {
          this.error = true;
          this.loading = false;
        });
    },
    renderMarkdown() {
      if (!this.article || !this.article.content) return;

      let converter = new showdown.Converter({
        tables: true,
        ghCodeBlocks: true,
        tasklists: true,
        strikethrough: true,
        ghCompatibleHeaderId: true,
        simpleLineBreaks: true,
      });

      let rawHtml = converter.makeHtml(this.article.content);
      // Sanitize: strip script/iframe/object/embed tags to mitigate XSS
      this.htmlContent = rawHtml
        .replace(/<script\b[^<]*(?:(?!<\/script>)<[^<]*)*<\/script>/gi, '')
        .replace(/<iframe\b[^>]*>.*?<\/iframe>/gi, '')
        .replace(/<object\b[^>]*>.*?<\/object>/gi, '')
        .replace(/<embed\b[^>]*\/?>/gi, '');

      this.$nextTick(() => {
        this.highlightCode();
        this.generateToc();
      });
    },
    highlightCode() {
      let contentEl = this.$refs.contentArea;
      if (!contentEl) return;
      let codeBlocks = contentEl.querySelectorAll('pre code');
      codeBlocks.forEach(block => {
        hljs.highlightElement(block);
      });
    },
    generateToc() {
      let contentEl = this.$refs.contentArea;
      if (!contentEl) return;
      let headings = contentEl.querySelectorAll('h1, h2, h3');
      let items = [];
      headings.forEach((heading, index) => {
        let id = heading.id || `heading-${index}`;
        heading.id = id;
        items.push({
          id: id,
          text: heading.textContent,
          level: parseInt(heading.tagName.charAt(1)),
        });
      });
      this.tocItems = items;
      if (items.length > 0) {
        this.activeHeadingId = items[0].id;
      }
    },
    handleScroll() {
      if (this.tocItems.length === 0) return;
      let containerTop = this.$el.getBoundingClientRect().top;

      let currentId = this.tocItems[0].id;
      for (let i = 0; i < this.tocItems.length; i++) {
        let el = document.getElementById(this.tocItems[i].id);
        if (el && el.getBoundingClientRect().top - containerTop <= 100) {
          currentId = this.tocItems[i].id;
        }
      }
      this.activeHeadingId = currentId;
    },
    scrollToHeading(id) {
      let el = document.getElementById(id);
      if (el) {
        el.scrollIntoView({ behavior: 'smooth', block: 'start' });
        this.activeHeadingId = id;
      }
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
$ink: #1d2129;
$ink-soft: #8a919f;
$line: #f0f0f2;
$bg: #f4f5f5;
$paper-bg: #faf6f0; // 纸张色（奶油色）

.tiny-blog-detail {
  width: 100%;
  height: 100%;
  overflow-y: auto;
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

  .state-wrap {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    min-height: 60%;
    color: $ink-soft;
    font-size: 15px;
    gap: 16px;
  }

  /* 居中的两栏布局：正文卡片 + 右侧栏 */
  .detail-layout {
    display: flex;
    align-items: flex-start;
    gap: 24px;
    max-width: 1200px;
    margin: 0 auto;
    padding: 24px 24px 60px;
    box-sizing: border-box;
  }

  .back-btn {
    color: $accent;
    text-decoration: none;
    font-size: 14px;
  }

  /* 正文卡片 */
  .article-card {
    flex: 1;
    min-width: 0;
    background: #fff;
    border-radius: 10px;
    padding: 40px 48px;
    box-sizing: border-box;
  }

  .back-link {
    display: inline-block;
    font-size: 13px;
    color: $ink-soft;
    text-decoration: none;
    margin-bottom: 20px;
    transition: color 0.2s;

    &:hover {
      color: $accent;
    }
  }

  .article-header {
    margin-bottom: 28px;
    padding-bottom: 20px;
    border-bottom: 1px solid $line;

    .article-title {
      font-size: 26px;
      font-weight: 700;
      color: $ink;
      margin: 0 0 14px;
      line-height: 1.4;
    }

    .article-meta {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 14px;
      font-size: 13px;
      color: $ink-soft;

      .meta-tag {
        background: rgba(30, 128, 255, 0.1);
        color: $accent;
        padding: 3px 10px;
        border-radius: 6px;
        font-weight: 500;
      }

      .meta-item {
        display: flex;
        align-items: center;
        gap: 4px;

        i {
          font-size: 14px;
        }
      }
    }
  }

  .content-area {
    line-height: 1.8;
    font-size: 16px;
    color: #303133;
    overflow-wrap: break-word;
    word-break: break-word;

    ::v-deep h1 {
      font-size: 28px;
      font-weight: 700;
      line-height: 1.3;
      margin: 48px 0 24px;
    }

    ::v-deep h2 {
      font-size: 24px;
      font-weight: 600;
      line-height: 1.3;
      margin: 40px 0 20px;
    }

    ::v-deep h3 {
      font-size: 20px;
      font-weight: 600;
      line-height: 1.4;
      margin: 32px 0 16px;
    }

    ::v-deep h4 {
      font-size: 18px;
      font-weight: 600;
      line-height: 1.4;
      margin: 28px 0 14px;
    }

    ::v-deep h5 {
      font-size: 16px;
      font-weight: 600;
      line-height: 1.5;
      margin: 24px 0 12px;
    }

    ::v-deep h6 {
      font-size: 14px;
      font-weight: 600;
      line-height: 1.5;
      margin: 20px 0 10px;
    }

    ::v-deep p {
      margin: 16px 0;
      line-height: 1.8;
    }

    ::v-deep br {
      display: block;
      content: "";
      margin-top: 8px;
    }

    ::v-deep pre {
      background-color: #f5f7fa;
      border-radius: 4px;
      padding: 16px;
      overflow-x: auto;
      margin: 16px 0;

      code {
        font-size: 13px;
        line-height: 1.5;
        font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
      }
    }

    ::v-deep code {
      font-size: 14px;
      font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
      background-color: #f5f7fa;
      padding: 2px 6px;
      border-radius: 3px;
    }

    ::v-deep pre code {
      background-color: transparent;
      padding: 0;
    }

    ::v-deep table {
      border-collapse: collapse;
      width: 100%;
      margin: 16px 0;

      th, td {
        border: 1px solid #ebeef5;
        padding: 8px 12px;
        text-align: left;
      }

      th {
        background-color: #f5f7fa;
        font-weight: 600;
      }
    }

    ::v-deep img {
      max-width: 100%;
      height: auto;
      display: block;
      margin: 16px auto 32px;
      border-radius: 4px;
    }

    ::v-deep blockquote {
      border-left: 4px solid #ebeef5;
      padding: 8px 16px;
      margin: 16px 0;
      color: #606266;
      background-color: #f5f7fa;
    }

    ::v-deep ul,
    ::v-deep ol {
      padding-left: 24px;
      margin: 12px 0;
    }

    // 覆盖全局 content.scss 中 `li { list-style-type: none }`,
    // 否则有序/无序列表只有缩进没有编号/圆点
    ::v-deep ul {
      list-style-type: disc;
    }

    ::v-deep ol {
      list-style-type: decimal;
    }

    ::v-deep li {
      margin: 4px 0;
      list-style-type: inherit;
    }

    ::v-deep a {
      color: $accent;
      text-decoration: none;

      &:hover {
        text-decoration: underline;
      }
    }
  }

  /* 右侧栏 */
  .side-rail {
    width: 220px;
    flex-shrink: 0;
    position: sticky;
    top: 24px;
  }

  .toc-card {
    background: #fff;
    border-radius: 10px;
    padding: 20px;
    box-sizing: border-box;

    .toc-head {
      font-size: 15px;
      font-weight: 600;
      color: $ink;
      margin-bottom: 12px;
      padding-bottom: 12px;
      border-bottom: 1px solid $line;
    }

    .toc-nav {
      display: flex;
      flex-direction: column;
    }

    .toc-item {
      display: block;
      font-size: 13px;
      color: #606266;
      text-decoration: none;
      padding: 6px 0;
      line-height: 1.4;
      transition: color 0.2s;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;

      &:hover {
        color: $accent;
      }

      &.active {
        color: $accent;
        font-weight: 600;
      }

      &.toc-level-2 {
        padding-left: 12px;
        font-size: 12px;
      }

      &.toc-level-3 {
        padding-left: 24px;
        font-size: 12px;
        color: #909399;
      }
    }
  }

  /* 响应式 */
  @media (max-width: 1024px) {
    .detail-layout {
      flex-direction: column;
    }

    .side-rail {
      width: 100%;
      position: static;
    }
  }

  @media (max-width: 768px) {
    .detail-layout {
      padding: 16px;
    }

    .article-card {
      padding: 24px 20px;
    }

    .side-rail {
      display: none;
    }
  }
}
</style>
