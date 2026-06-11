<!--
 * @Autor: huizhang43
 * @Date: 2026-06-01
 * @Description: tiny-blog文章编辑器
-->
<template>
  <div class="tiny-blog-editor">
    <div class="editor-header">
      <router-link class="back-link" to="/tiny-blog-manage">← 返回列表</router-link>
      <span class="editor-title">{{ isEdit ? '编辑文章' : '导入文章' }}</span>
    </div>

    <el-form ref="articleForm" :model="form" :rules="rules" label-width="80px" class="article-form">
      <el-form-item label="标题" prop="title">
        <el-input v-model="form.title" maxlength="255" placeholder="请输入文章标题"></el-input>
      </el-form-item>

      <el-form-item label="分类" prop="categoryId">
        <el-select v-model="form.categoryId" placeholder="选择分类" style="width: 100%">
          <el-option
            v-for="item in categories"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          ></el-option>
          <el-option key="__new__" label="+ 新建分类" value="__new__"></el-option>
        </el-select>
        <div v-if="form.categoryId === '__new__'" class="new-category-inline">
          <el-input
            v-model="newCategoryName"
            placeholder="输入新分类名称"
            size="small"
            style="width: 200px; margin-right: 8px"
          ></el-input>
          <el-button size="small" type="primary" @click="createCategory">确定</el-button>
        </div>
      </el-form-item>

      <el-form-item label="摘要">
        <el-input
          v-model="form.summary"
          type="textarea"
          :rows="2"
          :placeholder="summarizing ? 'AI 正在生成摘要...' : '请输入文章摘要'"
          :disabled="summarizing"
        ></el-input>
      </el-form-item>

      <el-form-item label="封面">
        <el-select v-model="form.coverImage" placeholder="选择封面图片" style="width: 100%">
          <el-option
            v-for="img in coverOptions"
            :key="img"
            :label="coverLabel(img)"
            :value="img"
          >
            <span style="display: flex; align-items: center;">
              <img :src="img" style="width: 40px; height: 30px; object-fit: cover; margin-right: 8px; border-radius: 2px;" />
              <span>{{ coverLabel(img) }}</span>
            </span>
          </el-option>
        </el-select>
      </el-form-item>
    </el-form>

    <div class="editor-wrap">
      <mavon-editor
        ref="mavonEditor"
        v-model="form.content"
        class="markdown-editor"
        :toolbars="toolbars"
        :scrollStyle="true"
        :ishljs="true"
        :tabSize="2"
        placeholder="开始编写文章内容..."
      ></mavon-editor>
    </div>

    <div class="editor-footer">
      <el-button plain @click="cancel">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="summarizing" @click="save">保存文章</el-button>
    </div>
  </div>
</template>

<script>
import axios from '@/network/blogSharonRequest.js';
import { mavonEditor } from 'mavon-editor';
import 'mavon-editor/dist/css/index.css';

export default {
  name: 'TinyBlogEditor',
  components: { mavonEditor },
  data() {
    return {
      isEdit: false,
      articleId: null,
      saving: false,
      summarizing: false,
      form: {
        title: '',
        publishDate: '',
        summary: '',
        coverImage: '',
        categoryId: '',
        author: '',
        content: '',
      },
      categories: [],
      newCategoryName: '',
      coverOptions: [],
      rules: {
        title: [{ required: true, message: '请输入文章标题', trigger: 'blur' }],
        categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
      },
      toolbars: {
        bold: true,
        italic: true,
        header: true,
        underline: true,
        mark: false,
        superscript: false,
        quote: true,
        ol: true,
        ul: true,
        link: true,
        imagelink: true,
        help: false,
        code: true,
        table: true,
        subfield: true,
        fullscreen: true,
        readmodel: false,
        undo: true,
        redo: true,
        trash: true,
        save: false,
        navigation: true,
        preview: true,
      },
    };
  },
  created() {
    this.fetchCategories();
    // 先加载封面选项,确保 el-select 设置 coverImage 时能匹配到 option,
    // 否则会回退显示原始 value(完整CDN URL)而非文件名
    this.fetchCovers().finally(() => {
      if (this.$route.query.id) {
        this.isEdit = true;
        this.articleId = this.$route.query.id;
        this.fetchArticle();
      } else if (this.$route.query.import) {
        this.loadImportDraft();
      }
    });
  },
  methods: {
    fetchCategories() {
      axios
        .get(`/tiny-blog/admin/categories`)
        .then(res => {
          if (res.data && res.data.data) {
            this.categories = res.data.data || [];
          }
        })
        .catch(() => {
          this.$message.error('分类加载失败');
        });
    },

    fetchArticle() {
      axios
        .get(`/tiny-blog/admin/posts/${this.articleId}`)
        .then(res => {
          if (res.data && res.data.data) {
            let article = res.data.data;
            this.form.title = article.title || '';
            this.form.publishDate = article.publishDate || '';
            this.form.summary = article.summary || '';
            this.form.coverImage = article.coverImage || '';
            this.form.categoryId = article.category ? article.category.id : '';
            this.form.author = article.author || '';
            this.form.content = article.content || '';
          }
        })
        .catch(() => {
          this.$message.error('文章加载失败');
        });
    },

    loadImportDraft() {
      try {
        const raw = sessionStorage.getItem('tinyBlogImportDraft');
        if (!raw) {
          this.$message.warning('未找到导入内容，请重新导入');
          return;
        }
        const draft = JSON.parse(raw);
        this.form.title = draft.title || '';
        this.form.summary = draft.summary || '';
        this.form.author = draft.author || '';
        this.form.publishDate = draft.publishDate || '';
        this.form.content = draft.content || '';
        this.$message.success('Markdown 导入成功，请选择分类和封面后保存');
        // front-matter 没带摘要时,调 AI 自动生成并填入
        if (!this.form.summary && this.form.content) {
          this.autoGenerateSummary();
        }
      } catch (e) {
        this.$message.error('导入内容解析失败，请重新导入');
      } finally {
        sessionStorage.removeItem('tinyBlogImportDraft');
      }
    },

    autoGenerateSummary() {
      this.summarizing = true;
      axios
        .post(`/tiny-blog/admin/posts/summary-preview`, { content: this.form.content })
        .then(res => {
          const summary = res.data && res.data.data && res.data.data.summary;
          if (summary) {
            this.form.summary = summary;
            this.$message.success('AI 摘要已生成,可继续修改');
          } else {
            this.$message.info('未生成摘要,请手动填写');
          }
        })
        .catch(() => {
          this.$message.warning('AI 摘要生成失败,请手动填写');
        })
        .finally(() => {
          this.summarizing = false;
        });
    },

    createCategory() {
      let name = this.newCategoryName.trim();
      if (!name) {
        this.$message.warning('请输入分类名称');
        return;
      }
      axios
        .post(`/tiny-blog/admin/categories`, { name })
        .then(res => {
          this.$message.success('分类创建成功');
          this.newCategoryName = '';
          this.fetchCategories();
          if (res.data && res.data.data) {
            this.form.categoryId = res.data.data.id;
          }
        })
        .catch(() => {
          this.$message.error('分类创建失败');
        });
    },

    save() {
      this.$refs.articleForm.validate(valid => {
        if (!valid) return;

        if (this.form.categoryId === '__new__') {
          this.$message.warning('请先创建分类或选择已有分类');
          return;
        }

        this.saving = true;
        let data = { ...this.form };
        if (!data.publishDate) {
          data.publishDate = new Date().toISOString().substring(0, 10);
        }

        let request;
        if (this.isEdit) {
          request = axios.put(`/tiny-blog/admin/posts/${this.articleId}`, data);
        } else {
          request = axios.post(`/tiny-blog/admin/posts`, data);
        }

        request
          .then(() => {
            this.$message.success(this.isEdit ? '文章更新成功' : '导入成功');
            this.$router.push('/tiny-blog-manage');
          })
          .catch(() => {
            this.$message.error('保存失败，请重试');
          })
          .finally(() => {
            this.saving = false;
          });
      });
    },

    cancel() {
      this.$router.push('/tiny-blog-manage');
    },

    fetchCovers() {
      return axios
        .get(`/tiny-blog/covers`)
        .then(res => {
          if (res.data && Array.isArray(res.data.data)) {
            this.coverOptions = res.data.data;
          }
        })
        .catch(() => {
          this.$message.error('封面列表加载失败');
        });
    },

    coverLabel(url) {
      // 从完整CDN URL中取文件名作为展示标签,如 .../01.jpg -> 01.jpg
      if (!url) return '';
      const parts = url.split('/');
      return parts[parts.length - 1] || url;
    },
  },
};
</script>

<style lang="scss" scoped>
.tiny-blog-editor {
  width: 100%;
  padding: 10px;

  .editor-header {
    display: flex;
    align-items: center;
    margin-bottom: 16px;

    .back-link {
      font-size: 14px;
      color: #606266;
      text-decoration: none;
      margin-right: 16px;

      &:hover {
        color: var(--red-500, #f56c6c);
      }
    }

    .editor-title {
      font-size: 18px;
      font-weight: 600;
      color: #303133;
    }
  }

  .article-form {
    margin-bottom: 16px;

    .new-category-inline {
      display: flex;
      align-items: center;
      margin-top: 8px;
    }
  }

  .editor-wrap {
    margin-bottom: 16px;

    .markdown-editor {
      width: 100%;
      min-height: 500px;
    }
  }

  .editor-footer {
    display: flex;
    justify-content: flex-end;
    gap: 12px;
    padding: 10px 0;
  }
}
</style>
