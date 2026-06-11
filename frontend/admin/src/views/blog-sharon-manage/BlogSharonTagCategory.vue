<!--
 * @Autor: huizhang43
 * @Date: 2026-06-01
 * @Description: blog-sharon分类和标签管理
-->
<template>
  <div class="blog-sharon-tag-category">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="分类管理" name="categories">
        <div class="toolbar">
          <el-input
            v-model="newCategoryName"
            placeholder="输入新分类名称"
            style="width: 240px"
            @keyup.enter.native="addCategory"
          ></el-input>
          <el-button type="primary" icon="el-icon-plus" @click="addCategory">添加分类</el-button>
        </div>

        <el-table :data="categories" v-loading="loading" style="width: 100%">
          <el-table-column prop="cateName" label="分类名称" min-width="200"></el-table-column>
          <el-table-column label="操作" width="120">
            <template slot-scope="scope">
              <el-button
                type="text"
                style="color: #f56c6c"
                @click="deleteCategory(scope.row)"
              >删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="标签管理" name="tags">
        <div class="toolbar">
          <el-input
            v-model="newTagName"
            placeholder="输入新标签名称"
            style="width: 240px"
            @keyup.enter.native="addTag"
          ></el-input>
          <el-button type="primary" icon="el-icon-plus" @click="addTag">添加标签</el-button>
        </div>

        <el-table :data="tags" v-loading="loading" style="width: 100%">
          <el-table-column prop="tagName" label="标签名称" min-width="200"></el-table-column>
          <el-table-column label="操作" width="120">
            <template slot-scope="scope">
              <el-button
                type="text"
                style="color: #f56c6c"
                @click="deleteTag(scope.row)"
              >删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script>
import axios from '@/network/blogSharonRequest.js';

export default {
  name: 'BlogSharonTagCategory',
  data() {
    return {
      activeTab: 'categories',
      categories: [],
      tags: [],
      newCategoryName: '',
      newTagName: '',
      loading: false,
    };
  },
  created() {
    this.fetchCategories();
    this.fetchTags();
  },
  methods: {
    fetchCategories() {
      this.loading = true;
      axios
        .get(`/blog-sharon/admin/categories`)
        .then(res => {
          if (res.data && res.data.data) {
            // 后端返回树形结构,扁平化显示
            this.categories = this.flattenCategories(res.data.data);
          }
        })
        .catch(() => {
          this.$message.error('分类加载失败，请刷新页面重试');
        })
        .finally(() => {
          this.loading = false;
        });
    },

    flattenCategories(tree) {
      let result = [];
      tree.forEach(node => {
        result.push({
          cateId: node.cateId,
          cateName: node.cateName,
          cateUrl: node.cateUrl,
        });
        if (node.children && node.children.length > 0) {
          result = result.concat(this.flattenCategories(node.children));
        }
      });
      return result;
    },

    fetchTags() {
      this.loading = true;
      axios
        .get(`/blog-sharon/admin/tags`)
        .then(res => {
          if (res.data && res.data.data) {
            this.tags = res.data.data || [];
          }
        })
        .catch(() => {
          this.$message.error('标签加载失败，请刷新页面重试');
        })
        .finally(() => {
          this.loading = false;
        });
    },

    addCategory() {
      let name = this.newCategoryName.trim();
      if (!name) {
        this.$message.warning('请输入分类名称');
        return;
      }
      axios
        .post(`/blog-sharon/admin/categories`, { cateName: name })
        .then(() => {
          this.$message.success('添加成功');
          this.newCategoryName = '';
          this.fetchCategories();
        })
        .catch(() => {
          this.$message.error('添加失败，请重试');
        });
    },

    addTag() {
      let name = this.newTagName.trim();
      if (!name) {
        this.$message.warning('请输入标签名称');
        return;
      }
      axios
        .post(`/blog-sharon/admin/tags`, { tagName: name })
        .then(() => {
          this.$message.success('添加成功');
          this.newTagName = '';
          this.fetchTags();
        })
        .catch(() => {
          this.$message.error('添加失败，请重试');
        });
    },

    deleteCategory(row) {
      this.$confirm(`确定要删除分类「${row.cateName}」吗？`, '删除分类', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      })
        .then(() => {
          axios
            .delete(`/blog-sharon/admin/categories/${row.cateId}`)
            .then(() => {
              this.$message.success('删除成功');
              this.fetchCategories();
            })
            .catch(err => {
              let msg = err.response?.data?.msg || '删除失败，请重试';
              this.$message.error(msg);
            });
        })
        .catch(() => {});
    },

    deleteTag(row) {
      this.$confirm(`确定要删除标签「${row.tagName}」吗？`, '删除标签', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      })
        .then(() => {
          axios
            .delete(`/blog-sharon/admin/tags/${row.tagId}`)
            .then(() => {
              this.$message.success('删除成功');
              this.fetchTags();
            })
            .catch(err => {
              let msg = err.response?.data?.msg || '删除失败，请重试';
              this.$message.error(msg);
            });
        })
        .catch(() => {});
    },
  },
};
</script>

<style lang="scss" scoped>
.blog-sharon-tag-category {
  width: 100%;
  height: calc(100% - 120px);
  padding: 10px;

  .toolbar {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 16px;
  }
}
</style>
