<!--
 * @Autor: huasenjio
 * @Date: 2022-10-04 09:39:40
 * @LastEditors: huasenjio
 * @LastEditTime: 2022-10-29 13:39:02
 * @Description: 
-->
<template>
  <div class="home-statistics">
    <el-skeleton :loading="loading" animated :throttle="200">
      <template slot="template">
        <el-row :gutter="10">
          <el-col :xs="24" :sm="12" :md="6" v-for="n in 4" :key="n">
            <section class="skeleton-card">
              <el-skeleton-item variant="text" style="width: 40%" />
              <el-skeleton-item variant="h1" style="width: 60%; margin-top: 12px" />
              <el-skeleton-item variant="text" style="width: 50%; margin-top: 8px" />
            </section>
          </el-col>
        </el-row>
      </template>
      <template slot="default">
        <el-row :gutter="10">
          <el-col :xs="24" :sm="12" :md="6" v-for="(item, index) in Object.values(statisticsMap)" :key="index">
            <section class="stat-card" :style="{ backgroundColor: item.color }">
              <div class="left">
                <div class="label text">{{ item.label | emptyTip }}</div>
                <div class="value text">{{ statisticsData[item.key] | emptyTip }}</div>
                <div class="rate text">{{ statisticsData[item.rateKey] | emptyTip }} 同昨日相比</div>
              </div>
              <div class="right">
                <i :class="item.icon"></i>
              </div>
            </section>
          </el-col>
          <!-- 快捷入口卡：替换原用户数卡片，样式与指标卡统一 -->
          <el-col :xs="24" :sm="12" :md="6">
            <section class="quick-card">
              <div class="quick-title text">快捷入口</div>
              <div class="quick-btns">
                <div class="quick-btn" @click="goImportArticle" aria-label="快捷操作: 导入文章">
                  <i class="iconfont icon-pen-fill"></i>
                  <span class="text">导入文章</span>
                </div>
                <div class="quick-btn" @click="goAddSite" aria-label="快捷操作: 新增网链">
                  <i class="iconfont icon-navigation-fill"></i>
                  <span class="text">新增网链</span>
                </div>
              </div>
            </section>
          </el-col>
        </el-row>
      </template>
    </el-skeleton>
  </div>
</template>

<script>
export default {
  name: 'HomeStatistics',
  data() {
    return {
      statisticsMap: {
        site: {
          color: 'var(--blue-400)',
          icon: 'iconfont icon-navigation-fill',
          label: '网址总数',
          key: 'siteCount',
          rateKey: 'siteRate',
        },
        column: {
          color: 'var(--green-400)',
          icon: 'iconfont icon-folder-line',
          label: '分类总数',
          key: 'columnCount',
          rateKey: 'columnRate',
        },
        article: {
          color: 'var(--indigo-400)',
          icon: 'iconfont icon-pen-fill',
          label: '文章',
          key: 'articleCount',
          rateKey: 'articleRate',
        },
      },
      statisticsData: {},
      loading: true,
    };
  },

  mounted() {
    this.queryOverview();
  },

  // activated() {
  //   this.queryOverview();
  // },

  methods: {
    queryOverview() {
      this.loading = true;
      this.API.dashboardOverview(
        {},
        {
          notify: false,
        },
      ).then(res => {
        this.statisticsData = res.data;
        this.loading = false;
      }).catch(() => {
        // 接口失败时也关闭骨架屏，避免无限 loading
        this.loading = false;
      });
    },

    // 快捷入口：跳转在档管理并自动唤起文件选择
    goImportArticle() {
      // 动作经 store 传递，跳转时不带 query，避免 WrapRight 的 tab 系统因 query 变化销毁目标组件
      this.$store.commit('commitAll', { pendingAction: 'import' });
      this.$router.push({ path: '/tiny-blog-manage' });
    },

    // 快捷入口：跳转网链管理并自动打开新增表单
    goAddSite() {
      this.$store.commit('commitAll', { pendingAction: 'add' });
      this.$router.push({ path: '/site-manage' });
    },
  },
};
</script>

<style lang="scss" scoped>
.home-statistics {
  width: 100%;
  margin-top: 8px;
  // el-row 负责栅格，el-col 负责响应式宽度（md=6 四列 / sm=12 两列 / xs=24 单列）
  .el-col {
    margin-bottom: 10px;
  }
  .skeleton-card {
    height: 110px;
    padding: 16px;
    border-radius: 6px;
    box-sizing: border-box;
    border: 1px solid var(--gray-50);
    background-color: var(--gray-0);
  }
  .stat-card {
    height: 110px;
    border-radius: 6px;
    overflow: hidden;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
    transition: transform 0.2s ease, box-shadow 0.2s ease;
    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 6px 16px rgba(0, 0, 0, 0.16);
    }
    .left {
      display: inline-block;
      width: 70%;
      padding: 12px;
      color: var(--gray-50);
      .value {
        font-size: 22px;
        font-weight: 600;
        margin-top: 4px;
      }
      .rate {
        font-size: 12px;
        opacity: 0.8;
        margin-top: 4px;
      }
    }
    .right {
      display: inline-flex;
      width: 30%;
      height: 110px;
      justify-content: center;
      align-items: center;
      vertical-align: top;
      i {
        display: block;
        font-size: 54px;
        opacity: 0.5;
        color: var(--gray-0);
      }
    }
  }
  .quick-card {
    height: 110px;
    padding: 12px;
    border-radius: 6px;
    box-sizing: border-box;
    overflow: hidden;
    background-color: var(--orange);
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
    transition: transform 0.2s ease, box-shadow 0.2s ease;
    display: flex;
    flex-direction: column;
    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 6px 16px rgba(0, 0, 0, 0.16);
    }
    .quick-title {
      font-size: 14px;
      color: var(--gray-50);
      margin-bottom: 10px;
    }
    .quick-btns {
      flex: 1;
      display: flex;
      gap: 10px;
      .quick-btn {
        flex: 1;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: 6px;
        border-radius: 6px;
        cursor: pointer;
        background: rgba(255, 255, 255, 0.18);
        border: 1px solid rgba(255, 255, 255, 0.25);
        transition: background 0.2s ease, transform 0.2s ease;
        &:hover {
          transform: translateY(-2px);
          background: rgba(255, 255, 255, 0.32);
        }
        i {
          font-size: 24px;
          color: var(--gray-0);
        }
        span {
          font-size: 13px;
          color: var(--gray-0);
          font-weight: 500;
        }
      }
    }
  }
}
</style>
