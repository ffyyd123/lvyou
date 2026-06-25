/**
 * SVG 图标库 - 统一管理所有图标
 * 24x24 viewBox，stroke 方式渲染，支持 color/size 属性
 */

import { h } from 'vue'

// 通用 SVG 图标工厂
function createIcon(paths, defaultSize = 24) {
  return {
    functional: true,
    props: {
      size: { type: [Number, String], default: defaultSize },
      color: { type: String, default: 'currentColor' },
      strokeWidth: { type: [Number, String], default: 1.5 }
    },
    render(props) {
      const size = typeof props.size === 'number' ? props.size + 'px' : props.size
      return h('svg', {
        xmlns: 'http://www.w3.org/2000/svg',
        width: size,
        height: size,
        viewBox: '0 0 24 24',
        fill: 'none',
        stroke: props.color,
        'stroke-width': props.strokeWidth,
        'stroke-linecap': 'round',
        'stroke-linejoin': 'round',
        'aria-hidden': 'true'
      }, paths.map(d => h('path', { d })))
    }
  }
}

// 出行 / 地图
export const IconMap = createIcon([
  'M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z',
  'M12 7a3 3 0 1 0 0 6 3 3 0 0 0 0-6z'
])

export const IconMapPin = createIcon([
  'M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0z',
  'M12 7a3 3 0 1 0 0 6 3 3 0 0 0 0-6z'
])

export const IconGlobe = createIcon([
  'M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z',
  'M2 12h20',
  'M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z'
])

// 导航
export const IconCompass = createIcon([
  'M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z',
  'M16.24 7.76l-2.12 6.36-6.36 2.12 2.12-6.36 6.36-2.12z'
])

export const IconNavigation = createIcon([
  'M3 11l19-9-9 19-2-8-8-2z'
])

// 行程
export const IconCalendar = createIcon([
  'M19 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V5a2 2 0 0 0-2-2z',
  'M16 1v4M8 1v4M3 9h18'
])

export const IconClock = createIcon([
  'M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z',
  'M12 6v6l4 2'
])

export const IconRoute = createIcon([
  'M3 17l4-4-4-4',
  'M7 13h10a4 4 0 0 0 0-8h-1'
])

// 偏好
export const IconLandmark = createIcon([
  'M3 21h18',
  'M6 21V7l6-4 6 4v14',
  'M9 21v-6h6v6'
])

export const IconMountain = createIcon([
  'M3 20h18L12 4z',
  'M8 13l4-3 4 6'
])

export const IconUtensils = createIcon([
  'M18 2v20M6 2v20',
  'M18 8c2 0 4 1 4 4v8H14v-8c0-3 2-4 4-4z',
  'M6 2c-2 0-4 2-4 4v6h4V2z'
])

export const IconTarget = createIcon([
  'M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z',
  'M12 6a6 6 0 1 0 0 12 6 6 0 0 0 0-12z',
  'M12 10a2 2 0 1 0 0 4 2 2 0 0 0 0-4z'
])

// 想法
export const IconLightbulb = createIcon([
  'M9 18h6M10 22h4',
  'M12 2a7 7 0 0 0-7 7c0 2.4 1.2 4.5 3 5.7V17h8v-2.3c1.8-1.3 3-3.4 3-5.7a7 7 0 0 0-7-7z'
])

// 操作
export const IconSearch = createIcon([
  'M21 21l-6-6m2-5a7 7 0 1 1-14 0 7 7 0 0 1 14 0z'
])

export const IconSend = createIcon([
  'M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z'
])

export const IconSparkles = createIcon([
  'M9.937 15.5A2 2 0 0 0 8.5 14.063l-6.135-1.582a.5.5 0 0 1 0-.962L8.5 9.936A2 2 0 0 0 9.937 8.5l1.582-6.135a.5.5 0 0 1 .963 0L14.063 8.5A2 2 0 0 0 15.5 9.937l6.135 1.581a.5.5 0 0 1 0 .964L15.5 14.063a2 2 0 0 0-1.437 1.437l-1.582 6.135a.5.5 0 0 1-.963 0z'
])

export const IconRefresh = createIcon([
  'M21 2v6h-6M3 12a9 9 0 0 1 15.36-6.36L21 8M3 22v-6h6M21 12a9 9 0 0 1-15.36 6.36L3 16'
])

export const IconTrash = createIcon([
  'M3 6h18M8 6V4a1 1 0 0 1 1-1h6a1 1 0 0 1 1 1v2m3 0v14a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V6h14',
  'M10 11v6M14 11v6'
])

export const IconX = createIcon([
  'M18 6L6 18M6 6l12 12'
])

export const IconChevronRight = createIcon([
  'M9 18l6-6-6-6'
])

export const IconChevronDown = createIcon([
  'M6 9l6 6 6-6'
])

export const IconPlus = createIcon([
  'M12 5v14M5 12h14'
])

export const IconMinus = createIcon([
  'M5 12h14'
])

export const IconArrowRight = createIcon([
  'M5 12h14M12 5l7 7-7 7'
])

// 统计
export const IconBarChart = createIcon([
  'M18 20V10M12 20V4M6 20v-6'
])

export const IconActivity = createIcon([
  'M22 12h-4l-3 9L9 3l-3 9H2'
])

// 状态
export const IconAlertCircle = createIcon([
  'M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z',
  'M12 8v4M12 16h.01'
])

export const IconCheckCircle = createIcon([
  'M22 11.08V12a10 10 0 1 1-5.93-9.14',
  'M22 4L12 14.01l-3-3'
])

export const IconInfo = createIcon([
  'M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z',
  'M12 16v-4M12 8h.01'
])

// 历史
export const IconHistory = createIcon([
  'M12 8v4l3 3m6-3a9 9 0 1 1-6.22-8.69'
])

// 3D / 视图模式
export const IconLayers = createIcon([
  'M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5'
])

export const IconMaximize = createIcon([
  'M8 3H5a2 2 0 0 0-2 2v3m18 0V5a2 2 0 0 0-2-2h-3m0 18h3a2 2 0 0 0 2-2v-3M3 16v3a2 2 0 0 0 2 2h3'
])
