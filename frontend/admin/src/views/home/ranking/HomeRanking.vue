<!--
 * @Autor: huasenjio
 * @Date: 2026-06-11
 * @LastEditors: huasenjio
 * @LastEditTime: 2026-06-11
 * @Description: 文章访问排行榜
-->

<template>
  <div class="home-ranking">
    <el-skeleton :loading="loading" animated :throttle="200">
      <template slot="template">
        <el-skeleton-item variant="text" style="width: 100%; height: 300px" />
      </template>
      <template slot="default">
        <div v-if="rankingData.length > 0">
          <el-table :data="rankingData" stripe style="width: 100%">
            <el-table-column prop="title" label="文章标题" min-width="200"></el-table-column>
            <el-table-column prop="views" label="访问量" width="120" sortable></el-table-column>
            <el-table-column prop="source" label="来源" width="100">
              <template slot-scope="scope">
                <el-tag :type="scope.row.source === 'sharon' ? 'primary' : 'success'" size="small">
                  {{ scope.row.source === 'sharon' ? '已归档' : '在档' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <el-empty v-else description="暂无文章访问记录。用户浏览文章后，访问量将自动统计并显示排行。"></el-empty>
      </template>
    </el-skeleton>
  </div>
</template>

<script>
export default {
  name: 'HomeRanking',
  data() {
    return {
      loading: true,
      rankingData: []
    };
  },
  mounted() {
    this.getData();
  },
  methods: {
    getData() {
      this.loading = true;
      this.API.dashboardArticleRank({}, { notify: false })
        .then(res => {
          this.rankingData = res.data; // array of {title, views, source, id}
        })
        .catch(err => {
          console.error('文章排行加载失败', err);
        })
        .finally(() => {
          this.loading = false;
        });
    }
  }
};
</script>

<style lang="scss" scoped>
.home-ranking {
  width: 100%;
  padding: 16px;
  border-radius: 6px;
  box-sizing: border-box;
  border: 1px solid var(--gray-50);
  background: var(--gray-0);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
  transition: box-shadow 0.2s ease;
  &:hover {
    box-shadow: 0 6px 16px rgba(0, 0, 0, 0.12);
  }
}
</style>
