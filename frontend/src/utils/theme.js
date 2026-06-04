/**
 * 切换主题并通知同页面其他组件（如 SideBar）
 */
export function setTheme(value) {
  localStorage.setItem('kpl-theme', value)
  window.dispatchEvent(new CustomEvent('theme-changed', { detail: value }))
}

/**
 * 读取当前主题
 */
export function getTheme() {
  return localStorage.getItem('kpl-theme') || 'light'
}
