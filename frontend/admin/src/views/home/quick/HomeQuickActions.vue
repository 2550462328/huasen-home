<!--
 * @Autor: huasenjio
 * @Date: 2026-06-11
 * @LastEditors: huasenjio
 * @LastEditTime: 2026-06-11
 * @Description: 快捷操作卡片
-->

<template>
  <div class="quick-actions">
    <div class="action-card" @click="navigate('/site-manage')" aria-label="快捷操作: 添加网址">
      <i class="iconfont icon-navigation-fill"></i>
      <span>添加网址</span>
    </div>
    <div class="action-card" @click="navigate('/column-manage')" aria-label="快捷操作: 新建分类">
      <i class="iconfont icon-folder-line"></i>
      <span>新建分类</span>
    </div>
    <div class="action-card" @click="navigate('/tiny-blog-editor')" aria-label="快捷操作: 写文章">
      <i class="iconfont icon-pen-fill"></i>
      <span>写文章</span>
    </div>
  </div>
</template>

<script>
export default {
  name: 'HomeQuickActions',
  methods: {
    navigate(path) {
      this.$router.push(path);
    }
  }
};
</script>

<style scoped lang="scss">
.quick-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 10px;
}
.action-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: var(--gray-0);
  border: 1px solid var(--gray-50);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;
  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 6px 16px rgba(0, 0, 0, 0.16);
  }
  i {
    font-size: 24px;
    color: var(--blue-400);
  }
  span {
    font-size: 14px;
    color: var(--gray-600);
    font-weight: 500;
  }
}
</style>
