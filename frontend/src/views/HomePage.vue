<template>
  <div class="home-page">
    <ParticleCanvas />
    <HeroSection />

    <div class="home-sections">
      <section class="section-block">
        <h2 class="section-title">
          <span class="gradient-text">数据概览</span>
        </h2>
        <StatsShowcase />
      </section>

      <section class="section-block">
        <h2 class="section-title">
          <span class="gradient-text">赛事工具</span>
        </h2>
        <QuickAccessCards />
      </section>

      <section class="section-block">
        <MatchTicker />
      </section>

      <footer class="home-footer">
        <p>KPL 赛事工具站 · 数据驱动电竞</p>
      </footer>
    </div>
  </div>
</template>

<script setup>
import ParticleCanvas from '../components/ParticleCanvas.vue'
import HeroSection from '../components/HeroSection.vue'
import StatsShowcase from '../components/StatsShowcase.vue'
import QuickAccessCards from '../components/QuickAccessCards.vue'
import MatchTicker from '../components/MatchTicker.vue'
</script>

<style scoped>
.home-page {
  min-height: 100vh;
  background: var(--bg);
  position: relative;
}

.home-sections {
  position: relative;
  z-index: 1;
  padding: 80px 0;
  display: flex;
  flex-direction: column;
  gap: 80px;
}

.section-block {
  width: 100%;
}

.section-title {
  text-align: center;
  font-size: 28px;
  font-weight: 800;
  margin: 0 0 40px;
  letter-spacing: -0.5px;
}

.home-footer {
  text-align: center;
  padding: 40px 24px;
  color: var(--muted);
  font-size: 13px;
  border-top: 1px solid var(--line);
  max-width: 600px;
  margin: 0 auto;
}
</style>
