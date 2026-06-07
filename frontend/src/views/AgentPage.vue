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

    <!-- 开发中占位（部署版本） -->
    <section v-if="isDevMode" class="dev-overlay">
      <div class="dev-card">
        <div class="dev-icon">AI</div>
        <h2>AI 复盘</h2>
        <div class="dev-badge">开发中</div>
        <p>该功能正在开发中，敬请期待</p>
      </div>
    </section>

    <!-- 正式聊天界面（本地开发） -->
    <template v-else>
      <section class="chat-area" ref="chatArea">
        <!-- 欢迎 -->
        <div v-if="!messages.length" class="welcome">
          <div class="welcome-icon">AI</div>
          <h2>KPL 赛事 AI 分析</h2>
          <p>输入问题，AI 自动查询赛事数据并生成分析报告</p>
          <div class="quick-grid">
            <button v-for="q in quickQuestions" :key="q" class="quick-btn" @click="askQuestion(q)">{{ q }}</button>
          </div>
        </div>

        <!-- 消息列表 -->
        <div v-for="(msg, idx) in messages" :key="idx" :class="['msg', msg.role]">
          <div class="msg-avatar">{{ msg.role === 'user' ? 'U' : 'AI' }}</div>
          <div class="msg-body">
            <div v-if="msg.dataItems && msg.dataItems.length" class="data-section">
              <div class="data-header">查询到 {{ msg.dataItems.length }} 条数据</div>
              <div class="data-list">
                <div v-for="(item, i) in msg.dataItems" :key="i" class="data-item">
                  <span class="data-idx">{{ i + 1 }}</span>
                  <span class="data-text">{{ formatDataItem(item) }}</span>
                </div>
              </div>
            </div>
            <div v-if="msg.reasoning" class="reasoning-section">
              <div class="reasoning-toggle" @click="msg.showReasoning = !msg.showReasoning">
                <span class="reasoning-arrow" :class="{ open: msg.showReasoning }">▸</span>
                思考过程
              </div>
              <div v-if="msg.showReasoning" class="reasoning-content">{{ msg.reasoning }}</div>
            </div>
            <div v-if="msg.text" class="msg-text" v-html="renderMarkdown(msg.text)"></div>
            <div v-if="msg.loading" class="typing-dots">
              <span></span><span></span><span></span>
            </div>
          </div>
        </div>
      </section>

      <section class="input-bar">
        <div class="input-wrap">
          <textarea
            v-model="inputText"
            placeholder="问点什么，比如：AG超玩会最近表现怎么样"
            rows="1"
            @keydown.enter.exact.prevent="sendMessage"
            @input="autoResize"
            ref="inputEl"
          />
          <button class="send-btn" :disabled="!inputText.trim() || streaming" @click="sendMessage">
            <span v-if="streaming" class="send-loading"></span>
            <span v-else>发送</span>
          </button>
        </div>
      </section>
    </template>
  </main>
</template>

<script setup>
import { nextTick, onMounted, ref, watch } from 'vue'
import { getTheme, setTheme } from '../utils/theme'

const isDevMode = import.meta.env.VITE_AI_DEV_MODE === 'true'

const theme = ref(getTheme())
watch(theme, (v) => setTheme(v))

const messages = ref([])
const inputText = ref('')
const streaming = ref(false)
const chatArea = ref(null)
const inputEl = ref(null)

const quickQuestions = [
  'AG超玩会最近表现怎么样',
  'ban率最高的英雄是谁',
  '马超的出装推荐',
  '重庆狼队阵容分析',
  '一诺的选手数据',
  '最近比赛复盘',
]

function renderMarkdown(text) {
  if (!text) return ''
  let s = text
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')

  s = s.replace(/((?:^\|.+\|$\n?)+)/gm, (block) => {
    const rows = block.trim().split('\n').filter(r => r.trim())
    if (rows.length < 2) return block
    const dataRows = rows.filter(r => !/^\|[\s:|-]+\|$/.test(r.trim()))
    if (dataRows.length < 1) return block
    const parseRow = (row) => row.split('|').slice(1, -1).map(c => c.trim())
    const headers = parseRow(dataRows[0])
    const bodyRows = dataRows.slice(1)
    let html = '<table style="border-collapse:collapse;margin:8px 0;width:100%;font-size:13px">'
    html += '<thead><tr>'
    headers.forEach(h => { html += `<th style="border:1px solid var(--mono-line);padding:6px 10px;background:var(--stat-bg);text-align:left;font-weight:700">${h}</th>` })
    html += '</tr></thead><tbody>'
    bodyRows.forEach(row => {
      const cells = parseRow(row)
      html += '<tr>'
      cells.forEach(c => { html += `<td style="border:1px solid var(--mono-line);padding:6px 10px">${c}</td>` })
      html += '</tr>'
    })
    html += '</tbody></table>'
    return html
  })

  s = s
    .replace(/^### (.+)$/gm, '<h4 style="margin:12px 0 4px;font-size:14px;font-weight:800;color:var(--mono-ink)">$1</h4>')
    .replace(/^## (.+)$/gm, '<h3 style="margin:14px 0 6px;font-size:15px;font-weight:800;color:var(--mono-ink)">$1</h3>')
    .replace(/^# (.+)$/gm, '<h2 style="margin:16px 0 8px;font-size:16px;font-weight:900;color:var(--mono-ink)">$1</h2>')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/`(.*?)`/g, '<code>$1</code>')
    .replace(/^\* (.+)$/gm, '<div style="padding-left:12px">• $1</div>')
    .replace(/\n/g, '<br>')

  return s
}

function formatDataItem(item) {
  if (!item) return ''
  const prefix = item.__section ? `[${sectionName(item.__section)}] ` : ''
  if (item.camp1TeamName && item.camp2TeamName) {
    return `${prefix}${item.camp1TeamName} ${item.camp1Score ?? '?'}:${item.camp2Score ?? '?'} ${item.camp2TeamName} | ${item.matchStageDesc || ''} | ${fmtTime(item.startTime)}`
  }
  if (item.playerName && item.teamName) {
    return `${prefix}${item.playerName} | ${item.teamName} | ${item.positionDesc || ''} | 胜率:${fmtPct(item.winRate)} | KDA:${fmtNum(item.avgKda)}`
  }
  if (item.heroName && item.pickRate !== undefined) {
    return `${prefix}${item.heroName} | 出场:${item.battleCount ?? 0} | Pick:${fmtPct(item.pickRate)} | Ban:${fmtPct(item.banRate)} | 胜率:${fmtPct(item.winRate)}`
  }
  if (item.teamName && item.battleCount !== undefined) {
    return `${prefix}${item.teamName} | 局数:${item.battleCount} | 胜率:${fmtPct(item.winRate)} | KDA:${fmtNum(item.avgKda)}`
  }
  if (item.title && item.url) {
    return `${prefix}${item.title} | ${item.snippet || ''} | ${item.url}`
  }
  const skip = ['id', 'matchId', 'leagueId', 'camp1TeamId', 'camp2TeamId', 'teamId', 'heroId', 'playerId', '__section']
  const text = Object.entries(item)
    .filter(([k, v]) => v != null && !skip.includes(k))
    .filter(([, v]) => typeof v !== 'object')
    .map(([, v]) => v)
    .join(' | ')
  return prefix + text
}

function flattenDataItems(data, section = '') {
  if (!data) return []
  if (Array.isArray(data)) {
    return data.map((item) => section && item && typeof item === 'object' ? { __section: section, ...item } : item)
  }
  if (typeof data === 'object') {
    if (Array.isArray(data.data)) {
      return flattenDataItems(data.data, section)
    }
    return Object.entries(data).flatMap(([key, value]) => flattenDataItems(value?.data ?? value, key))
  }
  return []
}

function sectionName(section) {
  const names = {
    teamStats: '战队',
    players: '选手',
    recentMatches: '比赛',
    webSearch: '联网',
    data: '数据'
  }
  return names[section] || section
}

function fmtTime(v) {
  if (!v) return ''
  return String(v).replace('T', ' ').slice(0, 16)
}
function fmtPct(v) {
  if (v == null) return '0'
  return (Number(v) * 100).toFixed(1) + '%'
}
function fmtNum(v) {
  if (v == null) return '0'
  return Number(v).toFixed(2)
}

function autoResize() {
  const el = inputEl.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 120) + 'px'
}

function scrollToBottom() {
  nextTick(() => {
    if (chatArea.value) chatArea.value.scrollTop = chatArea.value.scrollHeight
  })
}

function askQuestion(q) {
  inputText.value = q
  sendMessage()
}

async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || streaming.value) return

  messages.value.push({ role: 'user', text })
  inputText.value = ''
  autoResize()
  scrollToBottom()

  const msgIdx = messages.value.length
  messages.value.push({ role: 'ai', text: '', reasoning: '', showReasoning: false, dataItems: [], loading: true })
  streaming.value = true
  scrollToBottom()

  return new Promise((resolve) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', '/api/agent/chat/stream', true)
    xhr.setRequestHeader('Content-Type', 'application/json')
    xhr.setRequestHeader('Cache-Control', 'no-cache')

    let processedLen = 0
    let lastRender = 0
    let reasoningBuf = ''
    let contentStarted = false

    function processChunk() {
      const raw = xhr.responseText
      if (raw.length <= processedLen) return
      const chunk = raw.slice(processedLen)
      processedLen = raw.length

      const lines = chunk.split('\n')
      for (const line of lines) {
        const trimmed = line.trim()
        if (!trimmed || !trimmed.startsWith('data:')) continue
        const payload = trimmed.slice(5).trim()
        if (payload === '[DONE]') continue

        try {
          const evt = JSON.parse(payload)
          const msg = messages.value[msgIdx]
          if (evt.t === 'data') {
            let items = []
            if (Array.isArray(evt.d)) {
              items = evt.d
            } else if (evt.d && Array.isArray(evt.d.data)) {
              items = evt.d.data
            } else if (evt.d && evt.d.data && typeof evt.d.data === 'object') {
              items = flattenDataItems(evt.d.data)
              if (evt.d.webSearch?.data) {
                items.push(...flattenDataItems(evt.d.webSearch.data, 'webSearch'))
              }
            } else if (evt.d && typeof evt.d === 'object') {
              if (evt.d.error) {
                msg.dataError = evt.d.error
              } else {
                items = flattenDataItems(evt.d)
              }
            }
            msg.dataItems = items
            msg.loading = false
            scrollToBottom()
          } else if (evt.t === 'r') {
            reasoningBuf += evt.d
            msg.loading = false
          } else if (evt.t === 'c') {
            if (!contentStarted) {
              contentStarted = true
              if (reasoningBuf) {
                msg.reasoning = reasoningBuf
                msg.showReasoning = false
              }
            }
            msg.text += evt.d
            msg.loading = false
            const now = Date.now()
            if (now - lastRender > 30) {
              lastRender = now
              scrollToBottom()
            }
          }
        } catch {
          // skip non-JSON
        }
      }
    }

    xhr.onreadystatechange = () => {
      if (xhr.readyState === 3) {
        processChunk()
      }
      if (xhr.readyState === 4) {
        processChunk()
        const msg = messages.value[msgIdx]
        if (xhr.status >= 400) {
          msg.text = `服务异常 (${xhr.status}): ${xhr.responseText || xhr.statusText}`
        }
        if (!contentStarted && reasoningBuf) {
          msg.reasoning = reasoningBuf
          msg.showReasoning = true
        }
        msg.loading = false
        streaming.value = false
        scrollToBottom()
        resolve()
      }
    }

    xhr.onerror = () => {
      messages.value[msgIdx].text = '请求失败，请检查网络'
      messages.value[msgIdx].loading = false
      streaming.value = false
      scrollToBottom()
      resolve()
    }

    xhr.send(JSON.stringify({ message: text }))
  })
}

onMounted(() => {
  inputEl.value?.focus()
})
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

/* chat area */
.chat-area {
  flex: 1;
  overflow-y: auto;
  padding: 24px 0;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* welcome */
.welcome {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  text-align: center;
  gap: 12px;
}
.welcome-icon {
  width: 56px; height: 56px;
  display: grid; place-items: center;
  border: 2px solid var(--mono-line-strong);
  background: var(--stat-bg);
  font-size: 20px; font-weight: 900; color: var(--mono-ink);
  margin-bottom: 8px;
}
.welcome h2 { margin: 0; font-size: 22px; font-weight: 900; color: var(--mono-ink); }
.welcome p { margin: 0; font-size: 14px; color: var(--mono-dim); }
.quick-grid {
  display: flex; flex-wrap: wrap; justify-content: center; gap: 8px;
  margin-top: 20px; max-width: 560px;
}
.quick-btn {
  padding: 8px 16px;
  border: 1px solid var(--mono-line);
  background: var(--card-bg);
  color: var(--mono-soft);
  font-size: 13px; font-weight: 500;
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s, background 0.15s;
}
.quick-btn:hover {
  border-color: var(--accent);
  color: var(--mono-ink);
  background: var(--card-hover);
}

/* messages */
.msg {
  display: flex;
  gap: 12px;
  max-width: 720px;
  animation: msg-in 0.3s ease;
}
.msg.user { align-self: flex-end; flex-direction: row-reverse; }
.msg.ai { align-self: flex-start; }

@keyframes msg-in {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

.msg-avatar {
  width: 32px; height: 32px; min-width: 32px;
  display: grid; place-items: center;
  border: 1px solid var(--mono-line-strong);
  background: var(--stat-bg);
  font-size: 12px; font-weight: 900; color: var(--mono-ink);
}
.msg.user .msg-avatar { background: var(--mono-ink); color: var(--mono-bg); border-color: var(--mono-ink); }

.msg-body {
  padding: 12px 16px;
  border: 1px solid var(--mono-line);
  background: var(--card-bg);
  max-width: 640px;
}
.msg.user .msg-body {
  background: var(--mono-ink);
  color: var(--mono-bg);
  border-color: var(--mono-ink);
}

.msg-text {
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
}
.msg-text :deep(strong) { font-weight: 800; }
.msg-text :deep(code) {
  padding: 1px 5px;
  background: var(--stat-bg);
  border: 1px solid var(--mono-line);
  font-family: "Cascadia Mono", "Consolas", monospace;
  font-size: 12px;
}
.msg.user .msg-text :deep(code) {
  background: rgba(255, 255, 255, 0.15);
  border-color: rgba(255, 255, 255, 0.2);
}

/* data section */
.data-section {
  margin-bottom: 10px;
  padding-bottom: 10px;
  border-bottom: 1px dashed var(--mono-line);
}
.data-header {
  font-size: 11px;
  font-weight: 700;
  color: var(--mono-dim);
  letter-spacing: 1px;
  text-transform: uppercase;
  margin-bottom: 8px;
}
.data-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.data-item {
  display: flex;
  gap: 8px;
  align-items: baseline;
  font-size: 13px;
  line-height: 1.5;
  color: var(--mono-soft);
}
.data-idx {
  min-width: 18px;
  text-align: right;
  color: var(--mono-dim);
  font-size: 11px;
  font-weight: 700;
}
.data-text {
  font-family: "Cascadia Mono", "Consolas", monospace;
  font-size: 12px;
}

/* reasoning section */
.reasoning-section {
  margin-bottom: 10px;
}
.reasoning-toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--mono-dim);
  cursor: pointer;
  user-select: none;
}
.reasoning-toggle:hover { color: var(--mono-soft); }
.reasoning-arrow {
  display: inline-block;
  transition: transform 0.15s;
  font-size: 10px;
}
.reasoning-arrow.open { transform: rotate(90deg); }
.reasoning-content {
  margin-top: 6px;
  padding: 8px 10px;
  background: var(--stat-bg);
  border: 1px solid var(--mono-line);
  font-size: 12px;
  line-height: 1.6;
  color: var(--mono-dim);
  white-space: pre-wrap;
  word-break: break-word;
}

.typing-dots {
  display: flex; gap: 4px; padding-top: 4px;
}
.typing-dots span {
  width: 6px; height: 6px;
  background: var(--mono-dim);
  border-radius: 50%;
  animation: dot-bounce 1.2s ease-in-out infinite;
}
.typing-dots span:nth-child(2) { animation-delay: 0.15s; }
.typing-dots span:nth-child(3) { animation-delay: 0.3s; }
@keyframes dot-bounce {
  0%, 80%, 100% { opacity: 0.3; transform: scale(0.8); }
  40% { opacity: 1; transform: scale(1); }
}

/* input bar */
.input-bar {
  flex-shrink: 0;
  padding: 16px 0 20px;
  border-top: 1px solid var(--mono-line);
}
.input-wrap {
  display: flex;
  gap: 10px;
  align-items: flex-end;
  border: 1px solid var(--mono-line-strong);
  background: var(--card-bg);
  padding: 8px 10px 8px 14px;
}
.input-wrap textarea {
  flex: 1;
  border: none;
  background: transparent;
  color: var(--mono-ink);
  font-size: 14px;
  font-family: inherit;
  line-height: 1.5;
  resize: none;
  outline: none;
  min-height: 24px;
  max-height: 120px;
}
.input-wrap textarea::placeholder { color: var(--mono-dim); }
.send-btn {
  padding: 6px 18px;
  border: none;
  background: var(--mono-ink);
  color: var(--mono-bg);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  flex-shrink: 0;
  transition: opacity 0.15s;
}
.send-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.send-btn:hover:not(:disabled) { opacity: 0.85; }
.send-loading {
  display: inline-block;
  width: 14px; height: 14px;
  border: 2px solid var(--mono-bg);
  border-top-color: transparent;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

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

  .chat-area {
    padding: 14px 0;
    gap: 14px;
  }

  .welcome {
    justify-content: flex-start;
    padding-top: 10vh;
  }

  .welcome-icon {
    width: 46px;
    height: 46px;
    font-size: 16px;
  }

  .welcome h2 {
    font-size: 19px;
  }

  .welcome p {
    max-width: 280px;
    font-size: 13px;
  }

  .quick-grid {
    width: 100%;
    max-width: none;
    margin-top: 14px;
    justify-content: stretch;
  }

  .quick-btn {
    flex: 1 1 calc(50% - 4px);
    min-height: 38px;
    padding: 7px 8px;
    font-size: 12px;
  }

  .msg {
    max-width: 100%;
    gap: 8px;
  }

  .msg-avatar {
    width: 28px;
    height: 28px;
    min-width: 28px;
    font-size: 10px;
  }

  .msg-body {
    max-width: calc(100vw - 64px);
    padding: 10px 12px;
  }

  .msg.user .msg-body {
    max-width: calc(100vw - 78px);
  }

  .data-list {
    max-height: 168px;
    overflow-y: auto;
  }

  .data-text {
    font-size: 11px;
  }

  .reasoning-content {
    max-height: 140px;
    overflow-y: auto;
  }

  .input-bar {
    padding: 10px 0 0;
    background: transparent;
  }

  .input-wrap {
    padding: 7px 8px 7px 10px;
  }

  .input-wrap textarea {
    font-size: 14px;
  }

  .send-btn {
    min-width: 56px;
    padding: 6px 10px;
  }
}
</style>
