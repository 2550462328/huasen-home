<!--
 * @Autor: huizhang43
 * @Date: 2026-06-01
 * @Description: blog-sharon文章编辑器
-->
<template>
  <div class="blog-sharon-editor">
    <div class="editor-header">
      <router-link class="back-link" to="/blog-sharon-manage">← 返回列表</router-link>
      <span class="editor-title">{{ isEdit ? '编辑文章' : '新建文章' }}</span>
    </div>

    <el-alert
      v-if="isArchivedArticle"
      type="error"
      :closable="false"
      style="margin-bottom: 16px"
    >
      已归档的博客禁止编辑，仅可查看内容。如需修改，请联系管理员。
    </el-alert>

    <el-alert
      v-if="isHtmlArticle"
      type="warning"
      :closable="false"
      style="margin-bottom: 16px"
    >
      该文章原始格式为HTML，编辑时将自动转换为Markdown格式，部分复杂排版可能有格式差异。
    </el-alert>

    <el-form ref="articleForm" :model="form" :rules="rules" label-width="80px" class="article-form" :disabled="isArchivedArticle">
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

      <el-form-item label="标签">
        <el-select v-model="form.tagIds" multiple placeholder="选择标签" style="width: 100%">
          <el-option
            v-for="item in tags"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          ></el-option>
        </el-select>
      </el-form-item>

      <el-form-item label="摘要">
        <el-input v-model="form.summary" type="textarea" :rows="2" placeholder="请输入文章摘要"></el-input>
      </el-form-item>

      <el-form-item label="状态">
        <el-select v-model="form.status" placeholder="选择状态" style="width: 100%">
          <el-option label="已发布" :value="0"></el-option>
          <el-option label="草稿" :value="1"></el-option>
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
        :editable="!isArchivedArticle"
        placeholder="开始编写文章内容..."
      ></mavon-editor>
    </div>

    <div class="editor-footer">
      <el-button plain @click="cancel">{{ isArchivedArticle ? '返回' : '取消' }}</el-button>
      <el-button v-if="!isArchivedArticle" type="primary" :loading="saving" @click="save">保存文章</el-button>
    </div>
  </div>
</template>

<script>
import axios from '@/network/blogSharonRequest.js';
import { mavonEditor } from 'mavon-editor';
import 'mavon-editor/dist/css/index.css';
import TurndownService from 'turndown';

export default {
  name: 'BlogSharonEditor',
  components: { mavonEditor },
  data() {
    return {
      isEdit: false,
      isHtmlArticle: false,
      isArchivedArticle: true, // 已归档模块中的博客默认禁止编辑
      articleId: null,
      saving: false,
      form: {
        title: '',
        content: '',
        categoryId: '',
        tagIds: [],
        summary: '',
        status: 0,
      },
      categories: [],
      tags: [],
      newCategoryName: '',
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
    this.fetchTags();
    if (this.$route.query.id) {
      this.isEdit = true;
      this.articleId = this.$route.query.id;
      this.fetchArticle();
    }
  },
  methods: {
    fetchCategories() {
      axios
        .get(`/blog-sharon/admin/categories`)
        .then(res => {
          if (res.data && res.data.data) {
            // 后端返回树形结构,扁平化为选项列表
            this.categories = this.flattenCategories(res.data.data);
          }
        })
        .catch(() => {
          this.$message.error('分类加载失败');
        });
    },

    flattenCategories(tree) {
      let result = [];
      tree.forEach(node => {
        result.push({ id: node.cateId, name: node.cateName });
        if (node.children && node.children.length > 0) {
          result = result.concat(this.flattenCategories(node.children));
        }
      });
      return result;
    },

    fetchTags() {
      axios
        .get(`/blog-sharon/admin/tags`)
        .then(res => {
          if (res.data && res.data.data) {
            this.tags = res.data.data.map(t => ({ id: t.tagId, name: t.tagName }));
          }
        })
        .catch(() => {
          this.$message.error('标签加载失败');
        });
    },

    fetchArticle() {
      axios
        .get(`/blog-sharon/admin/posts/${this.articleId}`)
        .then(res => {
          if (res.data && res.data.data) {
            let article = res.data.data;
            this.form.title = article.postTitle || '';
            this.form.summary = article.postSummary || '';
            this.form.categoryId = article.categories && article.categories.length > 0 ? article.categories[0].cateId : '';
            this.form.tagIds = []; // tags 未迁移
            this.form.status = article.postStatus !== undefined ? article.postStatus : 0;

            let content = article.postContentMd || article.postContent || '';
            if (/<[a-z][\s\S]*>/i.test(content)) {
              this.isHtmlArticle = true;
              const turndownService = new TurndownService();
              content = turndownService.turndown(content);
            }
            this.form.content = content;
          }
        })
        .catch(() => {
          this.$message.error('文章加载失败');
        });
    },

    createCategory() {
      let name = this.newCategoryName.trim();
      if (!name) {
        this.$message.warning('请输入分类名称');
        return;
      }
      axios
        .post(`/blog-sharon/admin/categories`, { cateName: name })
        .then(res => {
          this.$message.success('分类创建成功');
          this.newCategoryName = '';
          this.fetchCategories();
          if (res.data && res.data.data) {
            this.form.categoryId = res.data.data.cateId;
          }
        })
        .catch(() => {
          this.$message.error('分类创建失败');
        });
    },

    save() {
      // 已归档文章禁止保存
      if (this.isArchivedArticle) {
        this.$message.warning('已归档的博客禁止编辑');
        return;
      }

      this.$refs.articleForm.validate(valid => {
        if (!valid) return;

        if (this.form.categoryId === '__new__') {
          this.$message.warning('请先创建分类或选择已有分类');
          return;
        }

        this.saving = true;
        let data = {
          postTitle: this.form.title,
          postContentMd: this.form.content,
          postSummary: this.form.summary,
          postStatus: this.form.status,
          cateIds: this.form.categoryId ? [this.form.categoryId] : [],
        };

        let request;
        if (this.isEdit) {
          request = axios.put(`/blog-sharon/admin/posts/${this.articleId}`, data);
        } else {
          request = axios.post(`/blog-sharon/admin/posts`, data);
        }

        request
          .then(() => {
            this.$message.success(this.isEdit ? '文章更新成功' : '文章创建成功');
            this.$router.push('/blog-sharon-manage');
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
      this.$router.push('/blog-sharon-manage');
    },
  },
};
</script>

<style lang="scss" scoped>
.blog-sharon-editor {
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
