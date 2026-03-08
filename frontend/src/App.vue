<template>
  <a-config-provider :locale="zhCN">
    <div id="app" class="h-screen flex flex-col">
      <RouterView />
    </div>
  </a-config-provider>
</template>

<script setup>
import { RouterView } from 'vue-router'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
</script>

<style>
@tailwind base;
@tailwind components;
@tailwind utilities;

#app {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}
</style>
