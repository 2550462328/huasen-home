<!--
 * @Autor: huasenjio
 * @Date: 2022-10-04 14:28:44
 * @LastEditors: huasenjio
 * @LastEditTime: 2023-06-15 00:48:04
 * @Description: 系统相关数据
-->

<template>
  <div class="home-system">
    <div class="store-group shadow">
      <div class="title">磁盘使用情况</div>
      <!-- 文字 -->
      <div class="disk-text text">
        <div class="text-item text">磁盘名：{{ disk.diskName }}</div>
        <div class="text-item text">空闲容量：{{ disk.freeValue }}</div>
        <div class="text-item text">使用占比：{{ disk.useUsage * 100 + '%' }}</div>
        <div class="text-item text">已用容量：{{ disk.useValue }}</div>
        <div class="text-item text">总磁盘容量：{{ disk.totalValue }}</div>
      </div>
      <!-- 水滴图 -->
      <div id="disk-chart"></div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'HomeSystem',

  data() {
    return {
      disk: {
        diskName: '默认磁盘',
        freeValue: '50G',
        totalValue: '100G',
        useValue: '50G',
        useUsage: 0.5,
      },
    };
  },

  mounted() {
    this.queryDiskOverview();
  },

  // activated() {
  //   this.queryDiskOverview();
  // },

  methods: {
    queryDiskOverview() {
      this.API.diskOverview({}, { notify: false }).then(res => {
        this.disk = res.data;
        this.initStoreChart();
      });
    },

    initStoreChart() {
      let option = {
        series: [
          {
            type: 'liquidFill',
            radius: '80%',
            color: [
              '#fbc607',
              '#21d0c3',
              this.$echarts.graphic.LinearGradient(0, 0, 0, 1, [
                {
                  offset: 0,
                  color: '#4293fd',
                },
                {
                  offset: 0.8,
                  color: '#4293fd',
                },
              ]),
            ],
            data: [this.disk.useUsage, this.disk.useUsage, this.disk.useUsage],
            center: ['70%', '50%'],
            label: {
              normal: {
                formatter: this.disk.useUsage * 100 + '%',
                fontSize: 18,
                fontWeight: 400,
                color: '#555',
              },
            },
            itemStyle: {
              opacity: 0.6, // 波浪的透明度
              shadowBlur: 0, // 波浪的阴影范围
            },
            emphasis: {
              itemStyle: {
                opacity: 0.8, // 鼠标经过波浪颜色的透明度
              },
            },
            outline: {
              borderDistance: 2,
              itemStyle: {
                borderColor: '#5fa5fa',
                borderWidth: 4,
                // shadowBlur: 20,
              },
            },
            backgroundStyle: {
              color: '#fff',
            },
          },
        ],
      };
      if (!this.storeChart) {
        let dom = document.getElementById('disk-chart');
        this.storeChart = this.$echarts.init(dom);
        // 监听容器尺寸变化，使用 requestAnimationFrame + ~100ms 防抖包裹 resize，
        // 避免断点切换时频繁触发 'ResizeObserver loop completed' 警告与卡顿。
        this.storeChartObserve = new ResizeObserver(entries => {
          if (this.resizeTimer) clearTimeout(this.resizeTimer);
          this.resizeTimer = setTimeout(() => {
            requestAnimationFrame(() => {
              if (this.storeChart) this.storeChart.resize();
            });
          }, 100);
        });
        this.storeChartObserve.observe(dom);
        this.$once('hook:beforeDestroy', function() {
          if (this.resizeTimer) clearTimeout(this.resizeTimer);
          this.storeChartObserve.unobserve(dom);
        });
      }
      this.storeChart.setOption(option);
    },

    bytesToSize(bytes) {
      if (bytes === 0) return '0 B';
      let k = 1000; // or 1024
      let sizes = ['B', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB'];
      let i = Math.floor(Math.log(bytes) / Math.log(k));
      return (bytes / Math.pow(k, i)).toPrecision(3) + ' ' + sizes[i];
    },
  },
};
</script>

<style lang="scss" scoped>
.home-system {
  width: 100%;
  padding: 0 10px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: var(--gray-600);
  .store-group {
    position: relative;
    width: 100%;
    height: 220px;
    padding: 10px;
    border: 1px solid var(--gray-50);
    .disk-text {
      position: absolute;
      top: 48px;
      .text-item {
        width: 140px;
        font-size: 14px;
        margin-top: 8px;
        color: var(--gray-500);
      }
    }
    #disk-chart {
      width: 100%;
      height: 180px;
    }
  }
}
</style>
