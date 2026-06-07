<template>
  <main class="app-shell has-sidebar agent-console" :class="`theme-${theme}`">
    <section class="command-strip">
      <div class="brand-block">
        <div class="brand-mark">K</div>
        <div>
          <p class="eyebrow">KPL DATA AGENT</p>
          <h1>AI 复盘</h1>
        </div>
      </div>
      <div class="status-line">
        <button class="theme-toggle" :title="theme === 'light' ? '切换暗色' : '切换亮色'" @click="theme = theme === 'light' ? 'dark' : 'light'">
          <span class="toggle-track" :class="{ on: theme === 'dark' }"><span class="toggle-thumb" /></span>
          <small>{{ theme === 'light' ? 'LIGHT' : 'DARK' }}</small>
        </button>
      </div>
    </section>

    <!-- 开发中占位 -->
    <section class="dev-overlay">
      <div class="dev-card">
        <div class="dev-icon">AI</div>
        <h2>AI 复盘</h2>
        <div class="dev-badge">开发中</div>
        <p>该功能正在开发中，敬请期待</p>
      </div>
    </section>
  </main>
</template>

<script setup>
import { ref, watch } from 'vue'
import { getTheme, setTheme } from '../utils/theme'

const theme = ref(getTheme())
watch(theme, (v) => setTheme(v))
</script>

<style scoped>
.agent-console {
  --mono-bg: #f8f5ec;
  --mono-panel: rgba(255, 255, 255, 0.92);
  --mono-line: rgba(0, 0, 0, 0.18);
  --mono-line-strong: rgba(0, 0, 0, 0.32);
  --mono-ink: #1a1a1a;
  --mono-soft: rgba(26, 26, 26, 0.65);
  --mono-dim: rgba(26, 26, 26, 0.4);
  --card-bg: #fff;
  --card-hover: rgba(0, 0, 0, 0.02);
  --stat-bg: rgba(0, 0, 0, 0.03);
  --input-bg: rgba(255, 255, 255, 0.7);
  --corner-border: rgba(0, 0, 0, 0.18);
  --accent: #1a1a1a;
  display: flex;
  flex-direction: column;
  max-width: none;
  height: 100vh;
  padding: 28px 32px 0 87px;
  color: var(--mono-ink);
  background: linear-gradient(180deg, rgba(250, 248, 240, 0.98), rgba(245, 242, 232, 0.99)), #f8f5ec;
}
.agent-console.theme-dark {
  --mono-bg: #0a0a0a;
  --mono-panel: rgba(18, 18, 18, 0.92);
  --mono-line: rgba(255, 255, 255, 0.18);
  --mono-line-strong: rgba(255, 255, 255, 0.32);
  --mono-ink: #e8e8e8;
  --mono-soft: rgba(232, 232, 232, 0.65);
  --mono-dim: rgba(232, 232, 232, 0.38);
  --card-bg: rgba(255, 255, 255, 0.06);
  --card-hover: rgba(255, 255, 255, 0.03);
  --stat-bg: rgba(255, 255, 255, 0.04);
  --input-bg: rgba(255, 255, 255, 0.06);
  --corner-border: rgba(255, 255, 255, 0.18);
  --accent: #e8e8e8;
  background: linear-gradient(180deg, #0a0a0a, #141414);
}

/* command strip */
.command-strip {
  position: relative; display: flex; align-items: center; justify-content: space-between;
  padding: 18px 22px; border: 1px solid var(--mono-line); background: var(--mono-panel);
  flex-shrink: 0;
}
.command-strip::before {
  content: ""; position: absolute; left: -1px; top: -1px; width: 36px; height: 36px;
  border-left: 2px solid var(--corner-border); border-top: 2px solid var(--corner-border); pointer-events: none;
}
.brand-block { display: flex; align-items: center; gap: 14px; }
.brand-mark {
  width: 42px; height: 42px; display: grid; place-items: center;
  border: 1px solid var(--mono-line-strong); color: var(--mono-ink); background: var(--stat-bg);
  font-size: 20px; font-weight: 900;
}
.eyebrow { margin: 0; color: var(--mono-dim); font-size: 10px; font-weight: 800; letter-spacing: 2px; text-transform: uppercase; }
h1 { margin: 0; color: var(--mono-ink); font-size: 20px; font-weight: 900; }
.status-line { display: flex; align-items: center; gap: 12px; }

/* theme toggle */
.theme-toggle {
  display: inline-flex; align-items: center; gap: 8px; padding: 0; border: 0; background: none; cursor: pointer;
  color: var(--mono-soft); font-size: 11px; font-weight: 700; letter-spacing: 1.5px;
}
.theme-toggle:hover { color: var(--mono-ink); }
.toggle-track {
  position: relative; width: 32px; height: 16px; border-radius: 8px;
  background: var(--mono-line-strong); transition: background 0.2s;
}
.toggle-track.on { background: var(--mono-ink); }
.toggle-thumb {
  position: absolute; top: 2px; left: 2px; width: 12px; height: 12px; border-radius: 50%;
  background: var(--mono-bg); transition: transform 0.2s;
}
.toggle-track.on .toggle-thumb { transform: translateX(16px); }

/* dev overlay */
.dev-overlay {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}
.dev-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  text-align: center;
}
.dev-icon {
  width: 64px; height: 64px;
  display: grid; place-items: center;
  border: 2px solid var(--mono-line-strong);
  background: var(--stat-bg);
  font-size: 24px; font-weight: 900; color: var(--mono-ink);
}
.dev-card h2 { margin: 0; font-size: 24px; font-weight: 900; color: var(--mono-ink); }
.dev-badge {
  display: inline-block;
  padding: 4px 16px;
  border: 1px solid var(--mono-line-strong);
  background: var(--stat-bg);
  color: var(--mono-soft);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 2px;
}
.dev-card p { margin: 0; font-size: 14px; color: var(--mono-dim); }

@media (max-width: 767px) {
  .agent-console {
    height: 100dvh;
    padding: 12px 12px calc(76px + env(safe-area-inset-bottom));
  }
  .command-strip {
    min-height: 56px;
    padding: 10px 12px;
  }
  .brand-mark {
    width: 34px;
    height: 34px;
    font-size: 16px;
  }
  h1 {
    font-size: 17px;
  }
  .theme-toggle small {
    display: none;
  }
}
</style>
