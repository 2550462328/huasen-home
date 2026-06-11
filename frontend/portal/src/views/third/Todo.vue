<!--
 * @Author: huizhang43 huizhang43@iflytek.com
 * @Date: 2024-01-10 10:41:19
 * @LastEditors: huizhang43 huizhang43@iflytek.com
 * @LastEditTime: 2024-01-10 10:41:56
 * @FilePath: \huasen-frontend\portal\src\views\third\Todo.vue
 * @Description: 这是默认设置,请设置`customMade`, 打开koroFileHeader查看配置 进行设置: https://github.com/OBKoro1/koro1FileHeader/wiki/%E9%85%8D%E7%BD%AE
-->
<template>
    <div class="container">
        <object :data="url" type="text/html" width="100%" height="100%"></object>
    </div>
</template>

<script>
export default {
    data(){
        return {url: "https://app.todoist.com/app/today",}
    },
}
</script>

<style lang="scss" scoped>
    .container {
        position: relative;
        height: 100% !important;
    }
</style>