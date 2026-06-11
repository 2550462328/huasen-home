<!--
 * @Autor: huasenjio
 * @Date: 2022-09-30 21:50:09
 * @LastEditors: huasenjio
 * @LastEditTime: 2022-10-07 10:07:47
 * @Description: 左侧导航条
-->
<template>
  <div class="wrap-left">
    <div class="logo">
      <div class="text title">{{ site.name }}后台管理</div>
    </div>
    <div class="navbar">
      <template v-for="(item, index) in navs">
        <div v-if="item.group" :key="'group-' + item.group" class="group-wrapper">
          <div v-if="index !== 0" class="group-separator"></div>
          <div class="group-header">{{ item.group }}</div>
        </div>
        <router-link v-else :to="item.path" :key="item.path">
          <div class="router-item">
            <i :class="item.iconClass"></i>
            <div class="name text">{{ item.name }}</div>
          </div>
        </router-link>
      </template>
    </div>
  </div>
</template>

<script>
import { mapState } from 'vuex';
import navs from '@/config/nav.config.js';
export default {
  name: 'WrapLeft',
  data() {
    return {
      navs,
    };
  },
  computed: {
    ...mapState(['site']),
  },
};
</script>

<style lang="scss" scoped>
.wrap-left {
  position: relative;
  width: 200px;
  height: 100%;
  overflow-x: hidden;
  overflow-y: auto;
  background-color: var(--gray-700);
  .logo {
    width: 100%;
    height: 60px;
    display: flex;
    justify-content: center;
    align-items: center;
    background-color: var(--gray-100);
    background-image: url('~@/assets/img/logo/favicon.svg');
    background-position: 18px center;
    background-size: 38px 38px;
    background-repeat: no-repeat;
    .title {
      position: relative;
      left: 20px;
      top: 5px;
      width: 120px;
      font-size: 20px;
    }
  }
  .navbar {
    .group-wrapper {
      .group-separator {
        height: 1px;
        background-color: var(--gray-600);
        margin: 8px 0;
      }
      .group-header {
        font-size: 12px;
        font-weight: 400;
        color: var(--gray-400);
        padding: 15px 20px 8px;
      }
    }
    .router-item {
      width: 100%;
      height: 54px;
      display: flex;
      align-items: center;
      padding: 15px 0;
      color: var(--gray-300);
      i {
        margin-left: 20px;
        font-size: 24px;
      }
      .name {
        margin-left: 10px;
        font-size: 16px;
      }
    }
    .router-link-active {
      .router-item {
        color: var(--gray-0);
        background-color: var(--gray-800);
      }
    }
  }
}
</style>
