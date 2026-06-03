<template>
  <LoadingScreen />
  <transition name="sidebar-enter">
    <SideBar v-if="route.meta.showSidebar" />
  </transition>
  <router-view v-slot="{ Component, route: r }">
    <transition name="page-zoom">
      <component :is="Component" :key="r.path" />
    </transition>
  </router-view>
</template>

<script setup>
import { useRoute } from 'vue-router'
import LoadingScreen from './components/LoadingScreen.vue'
import SideBar from './components/SideBar.vue'

const route = useRoute()

function onPageEnter(el) {
  const targets = el.querySelectorAll('.command-strip, .side-rail, .main-board, .panel')
  targets.forEach((child, i) => {
    child.style.opacity = '0'
    child.style.transform = 'translateY(18px)'
    child.style.transition = `opacity 0.4s ease ${i * 0.1}s, transform 0.4s ease ${i * 0.1}s`
    requestAnimationFrame(() => {
      child.style.opacity = '1'
      child.style.transform = 'translateY(0)'
    })
  })
}
</script>

<style>
.page-zoom-enter-active,
.page-zoom-leave-active {
  transition: opacity 0.4s ease, transform 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}
.page-zoom-enter-from {
  opacity: 0;
  transform: scale(1.04);
}
.page-zoom-leave-to {
  opacity: 0;
  transform: scale(0.96);
}
.page-zoom-leave-active {
  position: absolute;
  inset: 0;
}

.sidebar-enter-active {
  transition: opacity 0.3s ease 0.2s;
}
.sidebar-enter-from {
  opacity: 0;
}
.sidebar-leave-active {
  transition: none;
}
</style>
