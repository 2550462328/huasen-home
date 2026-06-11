<!--
 * @Autor: huizhang43
 * @Date: 2026-06-01
 * @Description: tiny-blog分类管理
-->
<template>
  <div class="tiny-blog-category-manage">
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
      <el-table-column prop="name" label="分类名称" min-width="200"></el-table-column>
      <el-table-column prop="postCount" label="文章数量" width="120"></el-table-column>
      <el-table-column label="操作" width="120">
        <template slot-scope="scope">
          <el-button
            type="text"
            style="color: #f56c6c"
            :disabled="scope.row.postCount > 0"
            @click="deleteCategory(scope.row)"
          >删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script>
import axios from '@/network/blogSharonRequest.js';

export default {
  name: 'TinyBlogCategoryManage',
  data() {
    return {
      categories: [],
      newCategoryName: '',
      loading: false,
    };
  },
  created() {
    this.fetchCategories();
  },
  methods: {
    fetchCategories() {
      this.loading = true;
      axios
        .get(`/tiny-blog/admin/categories`)
        .then(res => {
          if (res.data && res.data.data) {
            this.categories = res.data.data || [];
          }
        })
        .catch(() => {
          this.$message.error('分类加载失败，请刷新页面重试');
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
        .post(`/tiny-blog/admin/categories`, { name })
        .then(() => {
          this.$message.success('添加成功');
          this.newCategoryName = '';
          this.fetchCategories();
        })
        .catch(() => {
          this.$message.error('添加失败，请重试');
        });
    },

    deleteCategory(row) {
      if (row.postCount > 0) {
        this.$message.warning('该分类下还有文章，无法删除');
        return;
      }
      this.$confirm(`确定要删除分类「${row.name}」吗？`, '删除分类', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      })
        .then(() => {
          axios
            .delete(`/tiny-blog/admin/categories/${row.id}`)
            .then(() => {
              this.$message.success('删除成功');
              this.fetchCategories();
            })
            .catch(() => {
              this.$message.error('删除失败，请重试');
            });
        })
        .catch(() => {});
    },
  },
};
</script>

<style lang="scss" scoped>
.tiny-blog-category-manage {
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
