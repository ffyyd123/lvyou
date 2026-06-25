/**
 * Toast 消息通知
 * 轻量级实现，无需额外依赖
 */
import { createApp, h, Transition } from 'vue'

let toastContainer = null
let toastId = 0

function ensureContainer() {
  if (toastContainer) return toastContainer
  const div = document.createElement('div')
  div.id = 'toast-container'
  div.style.cssText = `
    position: fixed; top: 24px; right: 24px; z-index: 9999;
    display: flex; flex-direction: column; gap: 10px; pointer-events: none;
  `
  document.body.appendChild(div)
  toastContainer = div
  return toastContainer
}

const iconMap = {
  success: '✅',
  error: '❌',
  warning: '⚠️',
  info: 'ℹ️'
}

const colorMap = {
  success: '#10b981',
  error: '#ef4444',
  warning: '#f59e0b',
  info: '#4A90D9'
}

export function toast(message, type = 'info', duration = 3000) {
  const container = ensureContainer()
  const id = ++toastId

  const wrapper = document.createElement('div')
  wrapper.style.cssText = `
    display: flex; align-items: center; gap: 10px;
    padding: 14px 20px;
    background: rgba(20, 20, 40, 0.94);
    backdrop-filter: blur(12px);
    border-radius: 12px;
    color: white;
    font-size: 14px;
    font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif;
    box-shadow: 0 8px 32px rgba(0,0,0,0.3);
    border: 1px solid rgba(255,255,255,0.08);
    pointer-events: auto;
    animation: toastIn 0.3s ease;
    max-width: 420px;
  `

  wrapper.innerHTML = `
    <span style="font-size: 18px; flex-shrink: 0;">${iconMap[type] || iconMap.info}</span>
    <span style="flex:1; line-height: 1.4;">${message}</span>
  `
  container.appendChild(wrapper)

  setTimeout(() => {
    wrapper.style.animation = 'toastOut 0.3s ease forwards'
    wrapper.addEventListener('animationend', () => {
      wrapper.remove()
    })
  }, duration)

  return id
}

// 注入动画样式
const style = document.createElement('style')
style.textContent = `
  @keyframes toastIn {
    from { opacity: 0; transform: translateX(60px); }
    to { opacity: 1; transform: translateX(0); }
  }
  @keyframes toastOut {
    from { opacity: 1; transform: translateX(0); }
    to { opacity: 0; transform: translateX(60px); }
  }
`
document.head.appendChild(style)
