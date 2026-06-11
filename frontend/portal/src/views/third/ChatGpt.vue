<template>
    <div class="container">
        <object :data="url" type="text/html" width="100%" height="100%"></object>
    </div>
</template>

<script>
export default {
    data(){
        return {url: "https://chat.huitogo.club/",}
    },
}
</script>

<style lang="scss" scoped>
    .container {
        position: relative;
        height: 100% !important;
    }
</style>