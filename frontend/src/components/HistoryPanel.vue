<template>
  <div class="history-panel">
    <div class="panel-header">
      <button class="title-btn" type="button" @click="collapsed = !collapsed">
        <IconHistory size="17" />
        <span>历史记录</span>
        <span v-if="list.length" class="count-badge">{{ list.length }}</span>
        <IconChevronDown size="15" class="collapse-icon" :class="{ collapsed }" />
      </button>
      <button class="icon-btn" type="button" title="刷新历史记录" :disabled="loading" @click="fetchHistory">
        <IconRefresh size="15" :class="{ spinning: loading }" />
      </button>
    </div>

    <div v-show="!collapsed" class="panel-body">
      <div class="history-tools">
        <div class="search-box">
          <IconSearch size="15" />
          <input v-model.trim="keyword" type="search" placeholder="搜索城市、偏好或摘要" />
        </div>
        <span class="result-count">{{ filteredList.length }} 条</span>
      </div>

      <div v-if="loading && !list.length" class="state-box">
        <span class="mini-spinner"></span>
        <p>正在读取历史记录</p>
      </div>

      <div v-else-if="!filteredList.length" class="state-box">
        <IconInfo size="28" />
        <p>{{ keyword ? '没有匹配的路线' : '暂无历史记录' }}</p>
        <small>{{ keyword ? '换个关键词再试试' : '生成路线后会自动保存到这里' }}</small>
      </div>

      <div v-else class="history-list">
        <article
          v-for="item in filteredList"
          :key="item.id"
          :class="['history-item', { active: selectedId === item.id }]"
        >
          <button class="item-summary-row" type="button" @click="toggleItem(item)">
            <span class="route-dot"></span>
            <span class="route-main">
              <span class="route-title">
                <strong>{{ item.fromCity || '出发地' }}</strong>
                <IconArrowRight size="14" />
                <strong>{{ item.toCity || '目的地' }}</strong>
              </span>
              <span class="route-subtitle">{{ item.summary || firstTheme(item) || '路线已保存' }}</span>
            </span>
            <span class="route-time">{{ formatTime(item.createdAt) }}</span>
          </button>

          <div class="item-meta">
            <span>{{ item.days || parsedPlan(item)?.totalDays || '-' }} 天</span>
            <span>{{ item.preference || parsedPlan(item)?.preference || '未标注偏好' }}</span>
            <span v-if="item.poiCount">{{ item.poiCount }} 个景点</span>
          </div>

          <div v-if="selectedId === item.id" class="inline-detail">
            <template v-if="selectedPlan">
              <div class="detail-head">
                <div>
                  <div class="detail-title">{{ selectedPlan.from }} → {{ selectedPlan.to }}</div>
                  <div class="detail-subtitle">
                    {{ selectedPlan.totalDays }} 天 · {{ selectedPlan.preference || '综合体验' }}
                  </div>
                </div>
                <button class="load-btn" type="button" @click="loadPlan(selectedPlan)">
                  <IconMap size="15" />
                  加载路线
                </button>
              </div>

              <div v-if="selectedPlan.userIdea" class="user-idea">
                {{ selectedPlan.userIdea }}
              </div>

              <div class="day-preview-list">
                <div v-for="day in previewDays(selectedPlan)" :key="day.day" class="day-preview">
                  <div class="day-line">
                    <span class="day-index">D{{ day.day }}</span>
                    <strong>{{ day.theme || `第 ${day.day} 天` }}</strong>
                    <span>{{ day.pois?.length || 0 }} 点</span>
                  </div>
                  <div class="poi-line">
                    <span v-for="poi in day.pois?.slice(0, 3)" :key="poi.name">{{ poi.name }}</span>
                  </div>
                  <div class="day-stat">
                    <span>驾车 {{ formatMinutes(day.driveTime || day.drive_time) }}</span>
                    <span v-if="day.distance">约 {{ Number(day.distance).toFixed(0) }} km</span>
                  </div>
                </div>
              </div>

              <button
                v-if="selectedPlan.days?.length > 3"
                class="more-days"
                type="button"
                @click="showAllDays = !showAllDays"
              >
                {{ showAllDays ? '收起详情' : `展开其余 ${selectedPlan.days.length - 3} 天` }}
              </button>
            </template>

            <div v-else class="detail-error">
              历史数据格式异常，无法预览。可以删除后重新规划。
            </div>

            <div class="detail-actions">
              <button class="ghost-btn danger" type="button" :disabled="deleting" @click="deleteItem(item.id)">
                <IconTrash size="15" />
                删除
              </button>
            </div>
          </div>
        </article>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import axios from 'axios'
import { toast } from '../utils/toast.js'
import {
  IconArrowRight,
  IconChevronDown,
  IconHistory,
  IconInfo,
  IconMap,
  IconRefresh,
  IconSearch,
  IconTrash
} from '../icons/index.js'

const emit = defineEmits(['load-plan'])

const collapsed = ref(false)
const loading = ref(false)
const deleting = ref(false)
const keyword = ref('')
const list = ref([])
const selectedId = ref(null)
const selectedPlan = ref(null)
const showAllDays = ref(false)

const filteredList = computed(() => {
  const word = keyword.value.toLowerCase()
  if (!word) return list.value

  return list.value.filter((item) => {
    const text = [
      item.fromCity,
      item.toCity,
      item.preference,
      item.summary,
      item.userIdea
    ].filter(Boolean).join(' ').toLowerCase()
    return text.includes(word)
  })
})

onMounted(() => {
  fetchHistory()
})

defineExpose({
  reloadHistory: fetchHistory
})

async function fetchHistory() {
  loading.value = true
  try {
    const res = await axios.get('/api/history')
    list.value = res.data?.data || []
    if (selectedId.value && !list.value.some(item => item.id === selectedId.value)) {
      selectedId.value = null
      selectedPlan.value = null
    }
  } catch (e) {
    console.error('获取历史记录失败:', e)
    toast('获取历史记录失败', 'error')
  } finally {
    loading.value = false
  }
}

function toggleItem(item) {
  if (selectedId.value === item.id) {
    selectedId.value = null
    selectedPlan.value = null
    showAllDays.value = false
    return
  }

  selectedId.value = item.id
  selectedPlan.value = parsedPlan(item)
  showAllDays.value = false
}

function parsedPlan(item) {
  if (!item?.resultJson) return null
  try {
    return normalizePlan(JSON.parse(item.resultJson))
  } catch (e) {
    return null
  }
}

function normalizePlan(plan) {
  if (!plan || typeof plan !== 'object') return null

  return {
    ...plan,
    from: plan.from || plan.fromCity,
    to: plan.to || plan.toCity,
    totalDays: plan.totalDays || plan.days?.length || plan.days,
    routeDecisionReport: normalizeRouteDecisionReport(plan.routeDecisionReport || plan.route_decision_report),
    days: Array.isArray(plan.days)
      ? plan.days.map(day => ({
          ...day,
          driveTime: day.driveTime ?? day.drive_time,
          pois: Array.isArray(day.pois)
            ? day.pois.map(poi => ({
                ...poi,
                stayTime: poi.stayTime ?? poi.stay_time
              }))
            : []
        }))
      : []
  }
}

function normalizeRouteDecisionReport(report) {
  if (!report || typeof report !== 'object') return null

  return {
    ...report,
    selectedStrategy: report.selectedStrategy || report.selected_strategy,
    totalDistance: report.totalDistance ?? report.total_distance,
    totalDriveTime: report.totalDriveTime ?? report.total_drive_time,
    routeScore: report.routeScore ?? report.route_score,
    optimizationNotes: report.optimizationNotes || report.optimization_notes,
    candidates: Array.isArray(report.candidates)
      ? report.candidates.map(candidate => ({
          ...candidate,
          daysSummary: candidate.daysSummary || candidate.days_summary,
          totalDistance: candidate.totalDistance ?? candidate.total_distance,
          totalDriveTime: candidate.totalDriveTime ?? candidate.total_drive_time
        }))
      : []
  }
}

function firstTheme(item) {
  return parsedPlan(item)?.days?.[0]?.theme || ''
}

function previewDays(plan) {
  if (!plan?.days) return []
  return showAllDays.value ? plan.days : plan.days.slice(0, 3)
}

async function deleteItem(id) {
  if (deleting.value) return
  if (!confirm('确定删除这条历史记录吗？')) return

  deleting.value = true
  try {
    await axios.delete(`/api/history/${id}`)
    list.value = list.value.filter(item => item.id !== id)
    if (selectedId.value === id) {
      selectedId.value = null
      selectedPlan.value = null
    }
    toast('已删除历史记录', 'success')
  } catch (e) {
    console.error('删除失败:', e)
    toast('删除失败，请重试', 'error')
  } finally {
    deleting.value = false
  }
}

function loadPlan(data) {
  if (!data) return
  emit('load-plan', data)
  toast('已加载历史路线', 'success')
}

function formatMinutes(minutes) {
  const value = Number(minutes || 0)
  if (!value) return '未知'
  if (value < 60) return `${value} 分钟`
  const hours = Math.floor(value / 60)
  const rest = value % 60
  return rest ? `${hours} 小时 ${rest} 分钟` : `${hours} 小时`
}

function formatTime(timeStr) {
  if (!timeStr) return ''
  const d = new Date(timeStr)
  if (Number.isNaN(d.getTime())) return ''

  const now = new Date()
  const diff = now - d
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)

  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  if (hours < 24) return `${hours} 小时前`
  if (days < 7) return `${days} 天前`

  const month = d.getMonth() + 1
  const day = d.getDate()
  const hour = String(d.getHours()).padStart(2, '0')
  const min = String(d.getMinutes()).padStart(2, '0')
  return `${month}/${day} ${hour}:${min}`
}
</script>

<style scoped>
.history-panel {
  background: var(--color-surface);
}

.panel-header {
  height: 48px;
  padding: 0 14px 0 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  border-bottom: 1px solid var(--color-border-light);
}

.title-btn,
.icon-btn,
.item-summary-row,
.load-btn,
.ghost-btn,
.more-days {
  font-family: inherit;
}

.title-btn {
  min-width: 0;
  border: none;
  background: transparent;
  color: var(--color-text);
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--text-base);
  font-weight: 700;
  cursor: pointer;
}

.count-badge {
  min-width: 22px;
  height: 20px;
  padding: 0 7px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-full);
  background: var(--color-primary-light);
  color: var(--color-primary-dark);
  font-size: var(--text-xs);
  font-weight: 700;
}

.collapse-icon {
  transition: transform var(--transition-fast);
}

.collapse-icon.collapsed {
  transform: rotate(-90deg);
}

.icon-btn {
  width: 30px;
  height: 30px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg-alt);
  color: var(--color-text-secondary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.icon-btn:hover:not(:disabled) {
  background: var(--color-primary-light);
  color: var(--color-primary-dark);
  border-color: var(--color-primary);
}

.icon-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.spinning {
  animation: spin 0.8s linear infinite;
}

.panel-body {
  padding: 12px;
}

.history-tools {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.search-box {
  height: 36px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 10px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg-alt);
  color: var(--color-text-muted);
}

.search-box:focus-within {
  border-color: var(--color-primary);
  background: var(--color-surface);
  box-shadow: 0 0 0 3px rgba(14, 165, 233, 0.12);
}

.search-box input {
  width: 100%;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  color: var(--color-text);
  font-size: var(--text-sm);
}

.result-count {
  font-size: var(--text-xs);
  color: var(--color-text-muted);
  white-space: nowrap;
}

.state-box {
  min-height: 128px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  text-align: center;
  color: var(--color-text-muted);
  border: 1px dashed var(--color-border);
  border-radius: 8px;
  background: var(--color-bg-alt);
}

.state-box p {
  font-size: var(--text-base);
  font-weight: 700;
  color: var(--color-text-secondary);
}

.state-box small {
  font-size: var(--text-xs);
}

.mini-spinner {
  width: 18px;
  height: 18px;
  border: 2px solid var(--color-border);
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.history-item {
  border: 1px solid var(--color-border-light);
  border-radius: 8px;
  background: var(--color-bg-alt);
  overflow: hidden;
  transition: border-color var(--transition-fast), background var(--transition-fast), box-shadow var(--transition-fast);
}

.history-item:hover,
.history-item.active {
  border-color: rgba(14, 165, 233, 0.45);
  background: var(--color-surface);
}

.history-item.active {
  box-shadow: 0 0 0 3px rgba(14, 165, 233, 0.08);
}

.item-summary-row {
  width: 100%;
  min-height: 58px;
  border: none;
  background: transparent;
  padding: 10px 11px;
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr) auto;
  align-items: center;
  gap: 9px;
  text-align: left;
  cursor: pointer;
}

.route-dot {
  width: 8px;
  height: 32px;
  border-radius: var(--radius-full);
  background: linear-gradient(180deg, var(--color-primary), var(--color-accent));
}

.route-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.route-title {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text);
  font-size: var(--text-base);
}

.route-title strong {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.route-subtitle {
  color: var(--color-text-muted);
  font-size: var(--text-xs);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.route-time {
  color: var(--color-text-muted);
  font-size: var(--text-xs);
  white-space: nowrap;
}

.item-meta {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  padding: 0 11px 10px 30px;
}

.item-meta span {
  height: 22px;
  padding: 0 8px;
  display: inline-flex;
  align-items: center;
  border-radius: 6px;
  background: var(--color-surface);
  color: var(--color-text-secondary);
  border: 1px solid var(--color-border-light);
  font-size: var(--text-xs);
  font-weight: 600;
}

.inline-detail {
  padding: 12px;
  border-top: 1px solid var(--color-border-light);
  background: var(--color-surface);
}

.detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.detail-title {
  color: var(--color-text);
  font-weight: 800;
  font-size: var(--text-base);
}

.detail-subtitle {
  margin-top: 2px;
  color: var(--color-text-muted);
  font-size: var(--text-xs);
}

.load-btn {
  height: 34px;
  padding: 0 12px;
  border: none;
  border-radius: 8px;
  background: var(--color-primary);
  color: white;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--text-sm);
  font-weight: 700;
  cursor: pointer;
  white-space: nowrap;
}

.load-btn:hover {
  background: var(--color-primary-hover);
}

.user-idea {
  margin-bottom: 10px;
  padding: 8px 10px;
  border-radius: 8px;
  background: var(--color-accent-light);
  color: var(--color-text-secondary);
  font-size: var(--text-xs);
  line-height: var(--leading-relaxed);
}

.day-preview-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.day-preview {
  padding: 9px 10px;
  border: 1px solid var(--color-border-light);
  border-radius: 8px;
  background: var(--color-bg-alt);
}

.day-line {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  color: var(--color-text);
  font-size: var(--text-sm);
}

.day-line strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.day-line span:last-child {
  color: var(--color-text-muted);
  font-size: var(--text-xs);
}

.day-index {
  height: 22px;
  min-width: 34px;
  padding: 0 7px;
  border-radius: 6px;
  background: var(--color-primary-light);
  color: var(--color-primary-dark);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: var(--text-xs);
  font-weight: 800;
}

.poi-line {
  margin-top: 7px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.poi-line span {
  max-width: 100%;
  padding: 3px 7px;
  border-radius: 5px;
  background: var(--color-surface);
  color: var(--color-text-secondary);
  border: 1px solid var(--color-border-light);
  font-size: var(--text-xs);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.day-stat {
  margin-top: 7px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  color: var(--color-text-muted);
  font-size: var(--text-xs);
}

.more-days {
  width: 100%;
  height: 32px;
  margin-top: 8px;
  border: 1px dashed var(--color-border);
  border-radius: 8px;
  background: var(--color-bg-alt);
  color: var(--color-primary-dark);
  cursor: pointer;
  font-size: var(--text-sm);
  font-weight: 700;
}

.more-days:hover {
  background: var(--color-primary-light);
  border-color: var(--color-primary);
}

.detail-error {
  padding: 14px;
  border-radius: 8px;
  background: var(--color-error-light);
  color: var(--color-error);
  font-size: var(--text-sm);
}

.detail-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
}

.ghost-btn {
  height: 32px;
  padding: 0 10px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text-secondary);
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  font-size: var(--text-sm);
  font-weight: 700;
}

.ghost-btn.danger:hover:not(:disabled) {
  border-color: var(--color-error);
  background: var(--color-error-light);
  color: var(--color-error);
}

.ghost-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

@media (max-width: 520px) {
  .detail-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .load-btn {
    width: 100%;
    justify-content: center;
  }

  .route-time {
    display: none;
  }
}
</style>
