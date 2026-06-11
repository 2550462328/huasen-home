<!--
 * @Autor: huasenjio
 * @Date: 2021-12-05 20:23:00
 * @LastEditors: huizhang43 huizhang43@iflytek.com
 * @LastEditTime: 2023-12-27 17:52:32
 * @Description: 
-->
<template>
  <div v-rightMenu class="home-record">
    <header>
      <div class="title">
        设置
        <i class="iconfont icon-md-settings"></i>
      </div>
      <ul style="z-index: 3;">
        <li @click="openCustomWallpaper">
          <i class="iconfont icon-md-happy"></i>
          <span class="sm:hidden">墙纸</span>
        </li>
      </ul>
    </header>
    <CustomWallpaperDrawer v-if="showCustom" title="个性墙纸" :visible.sync="showCustom" :direction="'rtl'" :size="435"></CustomWallpaperDrawer>
  </div>
</template>
<script>
import { mapState } from 'vuex';
import CustomWallpaperDrawer from '@/components/content/customWallPaperDrawer/CustomWallpaperDrawer.vue';

export default {
  name: 'HomeRecord',
  components: { CustomWallpaperDrawer },
  data() {
    return {
      showCustom: false,
    };
  },
  computed: {
    ...mapState(['user']),
  },
  methods: {
    openCustomWallpaper() {
      this.showCustom = true;
    },
  },
};
</script>

<style lang="scss" scoped>
.home-record {
  width: 76%;
  margin: 285px auto 0 auto;
  padding: 10px 0 20px 0;
  header {
    padding: 10px 20px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    .title {
      z-index: 3;
      display: flex;
      align-items: center;
      font-size: 16px;
      font-weight: 500;
      i {
        font-size: 20px;
        font-weight: 500;
      }
    }
    ul {
      display: flex;
      align-items: center;
      font-size: 16px;
      font-weight: 500;
      li {
        margin-left: 10px;
        cursor: pointer;
        i {
          cursor: pointer;
        }
      }
    }
  }
}
</style>
