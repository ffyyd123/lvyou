<template>
  <div class="map-container">
    <div id="amap-container" ref="mapContainer"></div>

    <!-- 地图加载失败提示 -->
    <div v-if="mapError" class="map-error">
      <div class="error-card">
        <span class="error-icon">🗺️</span>
        <h3>地图加载失败</h3>
        <p>{{ mapError }}</p>
        <button class="retry-btn" @click="retryInit">🔄 重试</button>
      </div>
    </div>

    <!-- 工具栏：模式切换 + 样式切换 -->
    <div v-if="!mapError" class="map-toolbar">
      <!-- 视图模式切换 -->
      <div class="map-mode-bar">
        <button
          v-for="mode in mapModes"
          :key="mode.value"
          :class="['mode-btn', { active: currentMode === mode.value }]"
          @click.stop.prevent="switchMode(mode.value)"
        >
          <span class="mode-icon">{{ mode.icon }}</span>
          <span class="mode-label">{{ mode.label }}</span>
        </button>
      </div>

      <!-- 样式切换 -->
      <div class="map-style-bar">
        <button
          v-for="style in mapStyles"
          :key="style.value"
          :class="['style-btn', { active: currentStyle === style.value }]"
          @click.stop.prevent="switchStyle(style.value)"
        >
          <span class="style-dot" :style="{ background: style.color }"></span>
          <span class="style-label">{{ style.label }}</span>
        </button>
      </div>
    </div>

    <!-- 地图图例 -->
    <div class="map-legend" v-if="plan?.days && !mapError">
      <div class="legend-title">路线图例</div>
      <div
        v-for="day in plan.days"
        :key="day.day"
        :class="['legend-item', { active: selectedDay === day.day }]"
        @click.stop="$emit('day-select', day.day)"
      >
        <span class="legend-color" :style="{ background: safeDayColor(day.day - 1) }"></span>
        <span class="legend-label">Day {{ day.day }}: {{ day.theme }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, nextTick, onBeforeUnmount } from 'vue'
import { haversine } from '../utils/geo.js'
import { toast } from '../utils/toast.js'
import { loadAmap } from '../utils/amap.js'

const props = defineProps({
  plan: Object,
  selectedDay: Number,
  dayColors: Array
})

const emit = defineEmits(['day-select', 'route-rendered'])

const mapContainer = ref(null)
const mapError = ref('')
let map = null
let AMapInstance = null
let driving = null
let markers = []
let polylines = []
let segmentLabels = []
let scheduleMarkers = []
let infoWindows = []

const currentMode = ref('3D')
const currentStyle = ref('darkblue')

// 安全获取颜色（循环取模防止越界）
function safeDayColor(index) {
  if (!props.dayColors || props.dayColors.length === 0) return '#4A90D9'
  return props.dayColors[index % props.dayColors.length]
}

const mapModes = [
  { value: '2D', label: '2D 平面', icon: '🗺️' },
  { value: '3D', label: '3D 立体', icon: '🏙️' },
  { value: 'earth', label: '地球', icon: '🌍' }
]

const mapStyles = [
  { value: 'darkblue', label: '深邃蓝', color: '#0a1a3a' },
  { value: 'dark', label: '幻影黑', color: '#1a1a2e' },
  { value: 'macaron', label: '马卡龙', color: '#ffb6c1' },
  { value: 'fresh', label: '草色青', color: '#90ee90' },
  { value: 'light', label: '月光银', color: '#f0f0f0' },
  { value: 'whitesmoke', label: '远山黛', color: '#d4c5b9' },
  { value: 'graffiti', label: '涂鸦', color: '#ff6b6b' }
]

onMounted(async () => {
  await initMap()
})

onBeforeUnmount(() => {
  if (map) {
    map.destroy()
    map = null
  }
})

watch(() => props.plan, async (newPlan) => {
  if (newPlan && map) {
    await nextTick()
    renderRoute(newPlan)
  }
}, { deep: true })

watch(() => props.selectedDay, () => {
  updateDayHighlight()
})

async function retryInit() {
  mapError.value = ''
  await initMap()
}

async function initMap(options = {}) {
  // 检查 API Key
  const apiKey = import.meta.env.VITE_AMAP_KEY
  if (!apiKey || apiKey === 'YOUR_AMAP_KEY') {
    mapError.value = '请先在 .env 文件中配置高德地图 API Key（VITE_AMAP_KEY）'
    return
  }

  try {
    if (!AMapInstance) {
      AMapInstance = await loadAmap()
    }

    // 根据目的地动态设置初始中心点
    let center = options.center || [108.94, 34.26] // 默认中国中心
    if (props.plan?.days?.length > 0) {
      const firstDay = props.plan.days[0]
      const firstPoi = firstDay.pois?.[0]
      if (!options.center && firstPoi?.lng && firstPoi?.lat) {
        center = [firstPoi.lng, firstPoi.lat]
      }
    }

    if (map) {
      clearOverlays()
      map.destroy()
      map = null
    }

    const modeConfig = getModeConfig(currentMode.value)

    map = new AMapInstance.Map('amap-container', {
      zoom: options.zoom || modeConfig.zoom || 5,
      center: center,
      viewMode: modeConfig.viewMode,
      pitch: modeConfig.pitch,
      rotation: modeConfig.rotation,
      mapStyle: 'amap://styles/' + currentStyle.value,
      features: ['bg', 'road', 'building', 'point'],
      resizeEnable: true,
      showBuildingBlock: modeConfig.viewMode === '3D',
      buildingAnimation: modeConfig.viewMode === '3D',
      pitchEnable: modeConfig.viewMode === '3D',
      rotateEnable: modeConfig.viewMode === '3D',
      zooms: [2, 20],
      layers: [AMapInstance.createDefaultLayer()],
      wallColor: [30, 40, 80],
      roofColor: [50, 60, 120]
    })

    map.addControl(new AMapInstance.Scale())
    map.addControl(new AMapInstance.ToolBar({
      position: { top: 110, right: 20 }
    }))
    map.addControl(new AMapInstance.ControlBar({
      position: { top: 10, right: 20 },
      showZoomBar: true,
      showControlButton: true
    }))

    driving = new AMapInstance.Driving({
      policy: AMapInstance.DrivingPolicy.LEAST_TIME,
      extensions: 'base',
      hideMarkers: true
    })

    console.log(`🗺️ 高德地图初始化成功 (${currentMode.value})`)

    // 如果已有 plan 数据，立即渲染
    if (props.plan) {
      await nextTick()
      renderRoute(props.plan)
    }
  } catch (error) {
    console.error('地图加载失败:', error)
    const msg = error?.message || '未知错误'
    if (msg.includes('InvalidKey') || msg.includes('INVALID_USER_KEY')) {
      mapError.value = 'API Key 无效，请检查 .env 文件中的 VITE_AMAP_KEY 是否正确'
    } else if (msg.includes('network') || msg.includes('NetworkError')) {
      mapError.value = '网络连接失败，请检查网络后重试'
    } else {
      mapError.value = '地图服务加载失败，请稍后重试'
    }
  }
}

function getModeConfig(mode) {
  if (mode === '2D') {
    return {
      viewMode: '2D',
      pitch: 0,
      rotation: 0
    }
  }
  if (mode === 'earth') {
    return {
      viewMode: '3D',
      pitch: 45,
      rotation: 0,
      zoom: 3
    }
  }
  return {
    viewMode: '3D',
    pitch: 55,
    rotation: 0
  }
}

function getCurrentCenter() {
  if (!map || typeof map.getCenter !== 'function') return null
  const center = map.getCenter()
  if (!center) return null
  if (Array.isArray(center)) return center
  if (typeof center.getLng === 'function' && typeof center.getLat === 'function') {
    return [center.getLng(), center.getLat()]
  }
  if (typeof center.lng === 'number' && typeof center.lat === 'number') {
    return [center.lng, center.lat]
  }
  return null
}

async function switchMode(mode) {
  if (!map || currentMode.value === mode) return

  const center = getCurrentCenter()
  const zoom = typeof map.getZoom === 'function' ? map.getZoom() : undefined
  const previousMode = currentMode.value
  currentMode.value = mode

  try {
    await initMap({
      center,
      zoom: mode === 'earth' ? 3 : zoom
    })
  } catch (err) {
    currentMode.value = previousMode
    console.error('模式切换失败:', err)
    toast('模式切换失败', 'error')
  }
}

function switchStyle(style) {
  if (!map || currentStyle.value === style) return

  currentStyle.value = style

  try {
    map.setMapStyle('amap://styles/' + style)
  } catch (err) {
    console.error('样式切换失败:', err)
    toast('样式切换失败', 'error')
  }
}

function renderRoute(data) {
  if (!map || !data?.days) return

  clearOverlays()

  const allPois = []

  data.days.forEach((day, dayIndex) => {
    const color = safeDayColor(dayIndex)
    const pois = day.pois || []

    if (pois.length === 0) return

    pois.forEach((poi, poiIndex) => {
      if (!poi.lat || !poi.lng) return

      const position = [poi.lng, poi.lat]
      allPois.push(position)

      const markerContent = createMarkerContent(poi, dayIndex + 1, poiIndex + 1, color)

      const marker = new AMapInstance.Marker({
        position: position,
        content: markerContent,
        anchor: 'bottom-center',
        offset: new AMapInstance.Pixel(0, 0),
        zIndex: 100 - poiIndex
      })

      const infoWindow = new AMapInstance.InfoWindow({
        content: createInfoContent(poi, day),
        offset: new AMapInstance.Pixel(0, -40),
        closeWhenClickMap: true
      })

      let infoOpen = false
      marker.on('click', () => {
        if (infoOpen) {
          infoWindow.close()
          infoOpen = false
        } else {
          // 关闭其他 infoWindow
          infoWindows.forEach(i => i.close())
          infoWindows.forEach(i => i._open = false)
          infoWindow.open(map, marker.getPosition())
          infoOpen = true
          infoWindow._open = true
        }
      })

      marker.setMap(map)
      markers.push(marker)
      infoWindows.push(infoWindow)
    })

    if (pois.length >= 2) {
      const routePois = pois.filter(p => p.lat && p.lng)
      if (routePois.length >= 2) {
        renderRoadSegments(routePois, day, dayIndex + 1, color)
      }
    }

    renderScheduleMarkers(day, dayIndex + 1, color)
  })

  emit('route-rendered', {
    hasRoute: allPois.length > 0,
    markerCount: markers.length,
    polylineCount: polylines.length
  })

  if (allPois.length > 0) {
    fitRouteView()
  }

  updateDayHighlight()
}

function renderScheduleMarkers(day, dayNum, color) {
  const items = (day.schedule || []).filter(item => {
    return item.lat && item.lng && ['早餐', '午餐', '晚餐', '住宿'].includes(item.type)
  })

  items.forEach(item => {
    const marker = new AMapInstance.Marker({
      position: [item.lng, item.lat],
      anchor: 'bottom-center',
      zIndex: 70,
      content: `
        <div style="
          background: white;
          color: #0f172a;
          border: 2px solid ${color};
          border-radius: 16px;
          padding: 3px 8px;
          font-size: 11px;
          font-weight: 600;
          white-space: nowrap;
          box-shadow: 0 2px 8px rgba(0,0,0,0.22);
        ">
          ${scheduleIcon(item.type)} D${dayNum} ${item.type}
        </div>
      `
    })
    const infoWindow = new AMapInstance.InfoWindow({
      content: `
        <div style="padding:12px 14px;min-width:190px;max-width:280px;font-family:-apple-system,BlinkMacSystemFont,'PingFang SC',sans-serif;">
          <h4 style="margin:0 0 8px;font-size:15px;color:#0f172a;">${scheduleIcon(item.type)} ${item.title || item.type}</h4>
          <div style="font-size:13px;color:#475569;margin-bottom:6px;">${item.timeRange || item.period || ''}</div>
          <div style="font-size:13px;color:#0369a1;margin-bottom:6px;">${item.location || ''}</div>
          <div style="font-size:12px;color:#64748b;line-height:1.5;">${item.description || ''}</div>
        </div>
      `,
      offset: new AMapInstance.Pixel(0, -36),
      closeWhenClickMap: true
    })
    marker.on('click', () => {
      infoWindows.forEach(i => i.close())
      infoWindow.open(map, marker.getPosition())
    })
    marker.setMap(map)
    scheduleMarkers.push({ marker, day: dayNum })
    infoWindows.push(infoWindow)
  })
}

function scheduleIcon(type) {
  if (type === '住宿') return '🏨'
  if (type === '早餐' || type === '午餐' || type === '晚餐') return '🍽️'
  return '📌'
}

function fitRouteView() {
  if (!map) return
  const fitOverlays = [
    ...markers,
    ...polylines.map(item => item.polyline)
  ]
  if (fitOverlays.length === 0) return
  try {
    map.setFitView(fitOverlays, false, [60, 60, 60, 400])
  } catch (err) {
    console.warn('地图视野适配失败，路线标记已完成绘制:', err)
  }
}

function renderRoadSegments(pois, day, dayNum, color) {
  for (let index = 0; index < pois.length - 1; index++) {
    const from = pois[index]
    const to = pois[index + 1]
    drawRoadSegment(from, to, day, dayNum, index, color)
  }
}

function drawRoadSegment(from, to, day, dayNum, index, color) {
  const fallbackPath = [
    [from.lng, from.lat],
    [to.lng, to.lat]
  ]

  const drawFallback = () => {
    const polyline = createPolyline(fallbackPath, color, true)
    polyline.setMap(map)
    polylines.push({ polyline, day: dayNum, segmentIndex: index })
    createSegmentLabel(from, to, day, dayNum, index, color, false)
  }

  if (!driving || typeof driving.search !== 'function') {
    drawFallback()
    return
  }

  driving.search(
    new AMapInstance.LngLat(from.lng, from.lat),
    new AMapInstance.LngLat(to.lng, to.lat),
    (status, result) => {
      const steps = result?.routes?.[0]?.steps || []
      const roadPath = steps.flatMap(step => step.path || [])
      if (status !== 'complete' || roadPath.length < 2) {
        drawFallback()
        return
      }

      const polyline = createPolyline(roadPath, color, false)
      polyline.setMap(map)
      polylines.push({ polyline, day: dayNum, segmentIndex: index })
      createSegmentLabel(from, to, day, dayNum, index, color, true)
      fitRouteView()
      updateDayHighlight()
    }
  )
}

function createPolyline(path, color, fallback) {
  return new AMapInstance.Polyline({
    path,
    strokeColor: color,
    strokeWeight: fallback ? 4 : 6,
    strokeOpacity: fallback ? 0.45 : 0.88,
    lineJoin: 'round',
    lineCap: 'round',
    zIndex: fallback ? 45 : 55,
    showDir: true,
    dirColor: '#fff',
    strokeStyle: fallback ? 'dashed' : 'solid'
  })
}

function createSegmentLabel(from, to, day, dayNum, index, color, roadMatched) {
  const segment = day?.segments?.[index]
  const distance = segment?.distance ? `${Number(segment.distance).toFixed(1)} km` : ''
  const segmentDriveTime = segment?.driveTime ?? segment?.drive_time
  const time = segmentDriveTime ? formatDriveTime(segmentDriveTime) : ''
  const midpoint = [
    (Number(from.lng) + Number(to.lng)) / 2,
    (Number(from.lat) + Number(to.lat)) / 2
  ]
  const marker = new AMapInstance.Marker({
    position: midpoint,
    anchor: 'center',
    zIndex: 80,
    content: `
      <div style="
        background: rgba(15, 23, 42, 0.88);
        color: white;
        border: 1px solid ${color};
        border-radius: 14px;
        padding: 4px 9px;
        font-size: 11px;
        line-height: 1.3;
        white-space: nowrap;
        box-shadow: 0 2px 10px rgba(0,0,0,0.25);
      ">
        <span style="color:${color};font-weight:700;">D${dayNum}-${index + 1}</span>
        ${from.name} → ${to.name}
        ${distance ? ` · ${distance}` : ''}
        ${time ? ` · ${time}` : ''}
        <span style="opacity:.72;">${roadMatched ? '道路' : '估算'}</span>
      </div>
    `
  })
  marker.setMap(map)
  segmentLabels.push({ marker, day: dayNum })
}

function createMarkerContent(poi, dayNum, poiNum, color) {
  return `
    <div style="
      background: ${color};
      color: white;
      padding: 4px 10px;
      border-radius: 16px;
      font-size: 12px;
      font-weight: 600;
      white-space: nowrap;
      box-shadow: 0 2px 8px rgba(0,0,0,0.3);
      border: 2px solid white;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 4px;
    ">
      <span style="background: rgba(255,255,255,0.3); padding: 1px 6px; border-radius: 8px; font-size: 10px;">D${dayNum}</span>
      ${poi.name}
    </div>
  `
}

function createInfoContent(poi, day) {
  const stayTime = poi.stayTime
    ? (poi.stayTime >= 60
      ? Math.floor(poi.stayTime / 60) + '小时' + (poi.stayTime % 60 > 0 ? poi.stayTime % 60 + '分钟' : '')
      : poi.stayTime + '分钟')
    : '未知'

  return `
    <div style="
      padding: 14px 16px;
      min-width: 200px;
      max-width: 280px;
      font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', sans-serif;
    ">
      ${poi.imageUrl ? `
        <img src="${poi.imageUrl}" alt="${poi.name}" style="
          width: 100%;
          aspect-ratio: 4 / 3;
          object-fit: cover;
          border-radius: 8px;
          margin-bottom: 10px;
          background: #f1f5f9;
        " />
      ` : ''}
      <h4 style="margin: 0 0 8px; font-size: 16px; color: #2c3e50;">
        📍 ${poi.name}
      </h4>
      <div style="font-size: 13px; color: #666; margin-bottom: 6px;">
        🕐 建议停留: ${stayTime}
      </div>
      <div style="font-size: 13px; color: #666; margin-bottom: 6px;">
        📅 Day ${day.day} · ${day.theme || ''}
      </div>
      ${poi.reason ? `
        <div style="font-size: 13px; color: #0369a1; line-height: 1.5; margin-bottom: 6px;">
          ${poi.reason}
        </div>
      ` : ''}
      ${poi.tags ? `
        <div style="font-size: 12px; color: #64748b; margin-bottom: 6px;">
          ${poi.tags}
        </div>
      ` : ''}
      ${poi.description ? `
        <div style="font-size: 13px; color: #888; line-height: 1.6; margin-top: 8px; padding-top: 8px; border-top: 1px solid #eee;">
          ${poi.description}
        </div>
      ` : ''}
    </div>
  `
}

function updateDayHighlight() {
  polylines.forEach(({ polyline, day }) => {
    if (props.selectedDay === 0 || day === props.selectedDay) {
      polyline.setOptions({ strokeOpacity: 0.9, strokeWeight: 6 })
    } else {
      polyline.setOptions({ strokeOpacity: 0.3, strokeWeight: 3 })
    }
  })
  segmentLabels.forEach(({ marker, day }) => {
    if (props.selectedDay === 0 || day === props.selectedDay) {
      marker.show()
    } else {
      marker.hide()
    }
  })
  scheduleMarkers.forEach(({ marker, day }) => {
    if (props.selectedDay === 0 || day === props.selectedDay) {
      marker.show()
    } else {
      marker.hide()
    }
  })
}

function clearOverlays() {
  markers.forEach(m => m.setMap(null))
  polylines.forEach(p => p.polyline.setMap(null))
  segmentLabels.forEach(s => s.marker.setMap(null))
  scheduleMarkers.forEach(s => s.marker.setMap(null))
  infoWindows.forEach(i => i.close())
  markers = []
  polylines = []
  segmentLabels = []
  scheduleMarkers = []
  infoWindows = []
}

function formatDriveTime(minutes) {
  const value = Number(minutes || 0)
  if (!value) return ''
  if (value < 60) return `${value}分钟`
  const hours = Math.floor(value / 60)
  const rest = value % 60
  return rest ? `${hours}小时${rest}分钟` : `${hours}小时`
}
</script>

<style scoped>
.map-container {
  width: 100%;
  height: 100%;
  position: relative;
}

#amap-container {
  width: 100%;
  height: 100%;
}

/* 地图加载失败 */
.map-error {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #1a1a2e;
}

.error-card {
  text-align: center;
  color: white;
  padding: 40px;
}

.error-card .error-icon {
  font-size: 64px;
  display: block;
  margin-bottom: 16px;
}

.error-card h3 {
  font-size: 20px;
  margin-bottom: 8px;
  font-weight: 600;
}

.error-card p {
  font-size: 14px;
  color: rgba(255,255,255,0.6);
  margin-bottom: 24px;
  max-width: 400px;
  line-height: 1.6;
}

.retry-btn {
  padding: 10px 24px;
  background: linear-gradient(135deg, #4A90D9, #6C63FF);
  color: white;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  font-family: inherit;
}

.retry-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 16px rgba(74, 144, 217, 0.4);
}

/* 工具栏容器 */
.map-toolbar {
  position: absolute;
  top: 20px;
  right: 20px;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
  z-index: 1000;
  pointer-events: auto;
}

/* 视图模式切换栏 */
.map-mode-bar {
  display: flex;
  gap: 4px;
  background: rgba(10, 20, 40, 0.88);
  backdrop-filter: blur(12px);
  border-radius: 12px;
  padding: 5px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.35);
  border: 1px solid rgba(255, 255, 255, 0.08);
  pointer-events: auto;
}

.mode-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border: none;
  background: transparent;
  border-radius: 9px;
  cursor: pointer;
  font-size: 12px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.6);
  transition: all 0.25s ease;
  white-space: nowrap;
  font-family: inherit;
  pointer-events: auto;
  position: relative;
  z-index: 1;
}

.mode-btn:hover {
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.9);
}

.mode-btn.active {
  background: linear-gradient(135deg, #4A90D9, #6C63FF);
  color: white;
  box-shadow: 0 2px 12px rgba(74, 144, 217, 0.4);
}

.mode-icon {
  font-size: 15px;
  line-height: 1;
}

.mode-label {
  font-size: 12px;
}

/* 样式切换栏 */
.map-style-bar {
  display: flex;
  gap: 4px;
  background: rgba(10, 20, 40, 0.78);
  backdrop-filter: blur(12px);
  border-radius: 10px;
  padding: 4px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.3);
  border: 1px solid rgba(255, 255, 255, 0.06);
  pointer-events: auto;
}

.style-btn {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 5px 10px;
  border: none;
  background: transparent;
  border-radius: 7px;
  cursor: pointer;
  font-size: 11px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.5);
  transition: all 0.2s ease;
  white-space: nowrap;
  font-family: inherit;
  pointer-events: auto;
  position: relative;
  z-index: 1;
}

.style-btn:hover {
  background: rgba(255, 255, 255, 0.06);
  color: rgba(255, 255, 255, 0.8);
}

.style-btn.active {
  background: rgba(255, 255, 255, 0.12);
  color: white;
}

.style-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
  box-shadow: 0 0 6px currentColor;
}

.style-label {
  font-size: 11px;
}

/* 地图图例 */
.map-legend {
  position: absolute;
  bottom: 30px;
  left: 20px;
  background: rgba(10, 20, 40, 0.88);
  backdrop-filter: blur(12px);
  border-radius: 10px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.35);
  border: 1px solid rgba(255, 255, 255, 0.08);
  padding: 12px 16px;
  min-width: 180px;
  z-index: 100;
}

.legend-title {
  font-size: 12px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.9);
  margin-bottom: 8px;
  padding-bottom: 8px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 8px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.2s;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.7);
}

.legend-item:hover {
  background: rgba(255, 255, 255, 0.08);
}

.legend-item.active {
  background: rgba(74, 144, 217, 0.25);
  color: white;
}

.legend-color {
  width: 10px;
  height: 10px;
  border-radius: 3px;
  flex-shrink: 0;
}

.legend-label {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

@media (max-width: 768px) {
  .map-toolbar {
    top: 10px;
    left: 10px;
    right: 10px;
    align-items: center;
  }

  .map-mode-bar,
  .map-style-bar {
    max-width: 100%;
    overflow-x: auto;
  }
}
</style>
