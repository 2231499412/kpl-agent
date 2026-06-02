<template>
  <LoadingScreen />
  <SideBar v-if="route.meta.showSidebar" />
  <router-view v-slot="{ Component, route: r }">
    <transition name="page-fade" mode="out-in">
      <component :is="Component" :key="r.path" />
    </transition>
  </router-view>
</template>

<script setup>
import { useRoute } from 'vue-router'
import LoadingScreen from './components/LoadingScreen.vue'
import SideBar from './components/SideBar.vue'

const route = useRoute()
</script>

<style>
.page-fade-enter-active,
.page-fade-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}
.page-fade-enter-from {
  opacity: 0;
  transform: translateY(8px);
}
.page-fade-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
