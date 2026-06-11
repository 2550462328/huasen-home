<!--
 * @Autor: huasenjio
 * @Date: 2021-08-25 01:53:35
 * @LastEditors: huasenjio
 * @LastEditTime: 2026-06-11
 * @Description: 仪表盘页面
-->
<template>
  <div class="home">
    <!-- Row 1: 3 指标卡 + 快捷入口卡 -->
    <HomeStatistics></HomeStatistics>

    <!-- Row 2: 左侧访客趋势(宽) + 右侧文章排行(窄)，flex 等高对齐 -->
    <el-row :gutter="10" type="flex" class="dashboard-row" style="margin-top: 20px;">
      <el-col :md="16" :xs="24">
        <HomeVisitor></HomeVisitor>
      </el-col>
      <el-col :md="8" :xs="24">
        <HomeRanking></HomeRanking>
      </el-col>
    </el-row>
  </div>
</template>
<script>
import HomeStatistics from './statistics/HomeStatistics.vue';
import HomeVisitor from './visitor/HomeVisitor.vue';
import HomeRanking from './ranking/HomeRanking.vue';

export default {
  name: 'Home',

  components: {
    HomeStatistics,
    HomeVisitor,
    HomeRanking,
  },
};
</script>
<style lang="scss" scoped>
.home {
  width: 100%;
  // 等高行：让访客卡与文章排行卡高度一致；窄屏下换行回落为自然堆叠
  .dashboard-row {
    flex-wrap: wrap;
    align-items: stretch;
    .el-col {
      display: flex;
    }
  }
}
</style>
