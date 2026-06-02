<template>
  <div class="home-placeholder">
    <h1>KPL 赛事工具站</h1>
    <p>主页建设中...</p>
    <router-link to="/rankings">进入数据排行</router-link>
  </div>
</template>

<script setup>
</script>

<style scoped>
.home-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100vh;
  color: #e8e8e8;
  background: #0a0a0f;
}
.home-placeholder h1 {
  font-size: 48px;
  font-weight: 900;
  background: linear-gradient(135deg, #ff4444, #ff8800);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}
.home-placeholder a {
  margin-top: 24px;
  color: #ff4444;
}
</style>
