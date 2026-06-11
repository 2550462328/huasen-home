<!--
 * @Autor: huasenjio
 * @Date: 2022-10-04 10:39:39
 * @LastEditors: huasenjio
 * @LastEditTime: 2023-05-14 23:59:52
 * @Description: 访客统计
-->

<template>
  <div class="home-visitor">
    <div class="left shadow">
      <div class="total-text-group">
        <div class="left-text">
          <div class="label text">实时访客总数 (PV)</div>
          <div class="value-line">
            <span class="value text">{{ visitorCount | emptyTip }}</span>
            <span class="rate-badge" :class="rateClass">
              <i :class="rateIcon"></i>
              <span class="text">{{ visitorRate | emptyTip }}</span>
            </span>
            <span class="rate-tip text">同昨日比较</span>
          </div>
        </div>
        <el-radio-group v-model="granularity" size="mini" @change="handleGranularityChange">
          <el-radio-button label="day">按日</el-radio-button>
          <el-radio-button label="month">按月</el-radio-button>
        </el-radio-group>
      </div>
      <el-skeleton :loading="chartLoading" animated :throttle="200" class="chart-skeleton">
        <template slot="template">
          <el-skeleton-item variant="image" class="chart-skeleton-img" />
        </template>
        <template slot="default">
          <div v-if="visitor.time && visitor.time.length > 0" id="visitor-chart"></div>
          <el-empty v-else class="chart-empty" description="暂无访问趋势数据。访问量埋点上线后，每日凌晨自动快照，数据将在此展示。"></el-empty>
        </template>
      </el-skeleton>
    </div>
  </div>
</template>

<script>
export default {
  name: 'HomeVisitor',

  data() {
    return {
      visitor: {
        time: [],
        user: [],
        admin: [],
        other: []
      },
      visitorCount: 0,
      visitorRate: '',
      chartLoading: true,
      granularity: 'day',
    };
  },

  mounted() {
    this.queryVisitor();
  },

  computed: {
    // 同比方向：解析 visitorRate 的符号决定徽标配色与箭头
    rateValue() {
      const n = parseFloat(String(this.visitorRate).replace('%', ''));
      return Number.isNaN(n) ? 0 : n;
    },
    rateClass() {
      if (this.rateValue > 0) return 'is-up';
      if (this.rateValue < 0) return 'is-down';
      return 'is-flat';
    },
    rateIcon() {
      if (this.rateValue > 0) return 'el-icon-top';
      if (this.rateValue < 0) return 'el-icon-bottom';
      return 'el-icon-minus';
    },
  },

  // activated() {
  //   this.queryVisitor();
  // },

  methods: {
    handleGranularityChange() {
      this.queryVisitor();
    },

    queryVisitor() {
      this.chartLoading = true;
      this.API.dashboardPvTrend(
        { granularity: this.granularity },
        {
          notify: false,
        },
      ).then(res => {
        // Map response fields: dates→time, manage→admin
        this.visitor.time = res.data.dates || [];
        this.visitor.user = res.data.user || [];
        this.visitor.admin = res.data.manage || [];
        this.visitor.other = res.data.other || [];

        // Calculate total PV from last day (if data exists)
        if (this.visitor.user.length > 0) {
          const lastIndex = this.visitor.user.length - 1;
          this.visitorCount = (this.visitor.user[lastIndex] || 0) +
                              (this.visitor.admin[lastIndex] || 0) +
                              (this.visitor.other[lastIndex] || 0);
        }
        this.visitorRate = '0%'; // Rate calculation not implemented in backend

        this.chartLoading = false;
        this.$nextTick(() => {
          this.initVisitorChart();
        });
      }).catch(() => {
        // 接口失败时也关闭骨架屏，避免无限 loading
        this.chartLoading = false;
      });
    },

    initVisitorChart() {
      // Empty state guard
      if (!this.visitor.time || this.visitor.time.length === 0) {
        return;
      }

      let option = {
        grid: {
          left: '10',
          right: '10',
          top: '40',
          bottom: '40',
          containLabel: true,
        },
        tooltip: {
          show: true,
          trigger: 'axis',
        },
        legend: {
          show: true,
          x: '520',
          y: '10',
          icon: 'stack',
          itemWidth: 15,
          itemHeight: 10,
          data: ['用户', '管理员', '访客'],
        },
        xAxis: [
          {
            type: 'category',
            axisLine: {
              show: false,
            },
            axisLabel: {
              color: '#A1A7B3',
            },
            splitLine: {
              show: false,
            },
            axisTick: {
              show: false,
            },
            data: this.visitor.time,
          },
        ],
        dataZoom: [
          {
            show: true,
            height: 18,
            xAxisIndex: [0],
            left: '0',
            right: '6',
            bottom: '14',
            start: 0,
            end: 100,
            throttle: 100,
            filterMode: 'none',
            textStyle: {
              color: '#8c8c8c',
            },
            borderColor: '#8c8c8c',
          },
          {
            show: true,
            type: 'inside',
            height: 15,
            start: 2,
            end: 35,
            throttle: 100,
            filterMode: 'none',
          },
        ],
        yAxis: [
          {
            type: 'value',
            name: '流量监控',
            nameTextStyle: {
              fontSize: 14,
            },
            splitLine: {
              show: true,
              lineStyle: {
                color: '#A1A7B3',
                type: 'dashed',
              },
            },
            axisLine: {
              show: true,
              lineStyle: {
                color: '#8c8c8c',
              },
            },
            axisLabel: {
              show: true,
              margin: 10,
              textStyle: {
                color: '#A1A7B3',
              },
            },
            axisTick: {
              show: false,
            },
          },
        ],
        series: [
          {
            name: '用户',
            type: 'line',
            smooth: true,
            stack: '用户',
            symbolSize: 0,
            itemStyle: {
              normal: {
                color: '#4293FD',
                lineStyle: {
                  color: '#4293FD',
                  width: 2,
                },
              },
            },
            areaStyle: {
              normal: {
                color: this.$echarts.graphic.LinearGradient(
                  0,
                  0,
                  0,
                  1,
                  [
                    {
                      offset: 0,
                      color: 'rgba(19, 95, 172, 1)',
                    },
                    {
                      offset: 1,
                      color: 'rgba(112, 154, 195, 0.4)',
                    },
                  ],
                  false,
                ),
              },
            },
            data: this.visitor.user,
          },
          {
            name: '管理员',
            type: 'line',
            smooth: true,
            stack: '管理员',
            symbolSize: 0,
            itemStyle: {
              normal: {
                color: '#23D0C4',
                lineStyle: {
                  color: '#23D0C4',
                  width: 2,
                },
              },
            },
            areaStyle: {
              normal: {
                color: new this.$echarts.graphic.LinearGradient(
                  0,
                  0,
                  0,
                  1,
                  [
                    {
                      offset: 0,
                      color: 'rgba(50, 216, 205, 1)',
                    },
                    {
                      offset: 1,
                      color: 'rgba(35, 208, 196, 0.4)',
                    },
                  ],
                  false,
                ),
              },
            },
            data: this.visitor.admin,
          },
          {
            name: '访客',
            type: 'line',
            smooth: true,
            stack: '访客',
            symbolSize: 0,
            itemStyle: {
              normal: {
                color: '#fbc607',
                lineStyle: {
                  color: '#fbc607',
                  width: 2,
                },
              },
            },
            areaStyle: {
              normal: {
                color: new this.$echarts.graphic.LinearGradient(
                  0,
                  0,
                  0,
                  1,
                  [
                    {
                      offset: 0,
                      color: 'rgba(250, 199, 6, 1)',
                    },
                    {
                      offset: 1,
                      color: 'rgba(245, 133, 72, 0.4)',
                    },
                  ],
                  false,
                ),
              },
            },
            data: this.visitor.other,
          },
        ],
      };
      if (!this.chart) {
        let dom = document.getElementById('visitor-chart');
        this.chart = this.$echarts.init(dom);
        // 监听容器尺寸变化，使用 requestAnimationFrame + ~100ms 防抖包裹 resize，
        // 避免断点切换时频繁触发 'ResizeObserver loop completed' 警告与卡顿。
        this.chartObserve = new ResizeObserver(entries => {
          if (this.resizeTimer) clearTimeout(this.resizeTimer);
          this.resizeTimer = setTimeout(() => {
            requestAnimationFrame(() => {
              if (this.chart) this.chart.resize();
            });
          }, 100);
        });
        this.chartObserve.observe(dom);
        this.$once('hook:beforeDestroy', function() {
          if (this.resizeTimer) clearTimeout(this.resizeTimer);
          this.chartObserve.unobserve(dom);
        });
      }
      this.chart.setOption(option);
    },
  },
};
</script>

<style lang="scss" scoped>
.home-visitor {
  padding: 10px;
  display: flex;
  width: 100%;
  .left {
    width: 100%;
    min-height: 300px;
    padding: 16px;
    border-radius: 6px;
    box-sizing: border-box;
    display: flex;
    flex-direction: column;
    border: 1px solid var(--gray-50);
    background: var(--gray-0);
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
    transition: box-shadow 0.2s ease;
    &:hover {
      box-shadow: 0 6px 16px rgba(0, 0, 0, 0.12);
    }
    .total-text-group {
      width: 100%;
      height: 54px;
      flex-shrink: 0;
      display: flex;
      align-items: center;
      justify-content: space-between;
      .left-text {
        .label {
          font-size: 13px;
          color: var(--gray-500);
        }
        .value-line {
          display: flex;
          align-items: baseline;
          margin-top: 4px;
          .value {
            font-size: 26px;
            font-weight: 600;
            line-height: 1;
            color: var(--gray-800);
          }
          .rate-badge {
            display: inline-flex;
            align-items: center;
            gap: 2px;
            margin-left: 10px;
            padding: 2px 6px;
            border-radius: 10px;
            font-size: 12px;
            font-weight: 500;
            i {
              font-size: 12px;
            }
            &.is-up {
              color: var(--green-600);
              background: rgba(52, 211, 153, 0.15);
            }
            &.is-down {
              color: var(--red-600);
              background: rgba(248, 113, 113, 0.15);
            }
            &.is-flat {
              color: var(--gray-500);
              background: var(--gray-100);
            }
          }
          .rate-tip {
            margin-left: 8px;
            font-size: 12px;
            color: var(--gray-400);
          }
        }
      }
    }
    #visitor-chart {
      width: 100%;
      flex: 1;
      min-height: 0;
    }
    // 骨架屏包裹层需撑满剩余高度，否则图表 flex:1 量不到高度
    .chart-skeleton {
      flex: 1;
      min-height: 0;
      display: flex;
      flex-direction: column;
      ::v-deep > div {
        flex: 1;
        min-height: 0;
        display: flex;
        flex-direction: column;
      }
      .chart-skeleton-img {
        flex: 1;
        min-height: 0;
        height: auto;
      }
    }
    .chart-empty {
      flex: 1;
      display: flex;
      flex-direction: column;
      justify-content: center;
    }
  }
}
</style>
