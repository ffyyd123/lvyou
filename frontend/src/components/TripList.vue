<template>
  <div class="trip-list" v-if="plan && plan.days && plan.days.length > 0">
    <div class="list-header">
      <h3>📋 行程概览</h3>
      <span class="day-count">共 {{ plan.days.length }} 天</span>
    </div>

    <div v-if="plan.researchReport?.enabled" class="research-strip">
      <div class="research-title">
        <span>RAG 联网调研</span>
        <strong>{{ researchStatusText }}</strong>
      </div>
      <p class="research-summary" v-if="plan.researchReport?.evidenceSummary">
        {{ plan.researchReport.evidenceSummary }}
      </p>
      <div class="research-counts">
        <span>关键词 {{ totalKeywordCount }}/{{ plan.researchReport.targetKeywordCount || 15 }}</span>
        <span>每词最多 {{ plan.researchReport.targetSourcesPerKeyword || 20 }}</span>
        <span>目标 {{ plan.researchReport.targetEffectiveSourceCount || 300 }}</span>
        <span>原始 {{ plan.researchReport.rawSourceCount || 0 }}</span>
        <span>清洗 {{ plan.researchReport.cleanedSourceCount || 0 }}</span>
      </div>
      <div class="keyword-plan" v-if="keywordGroups.length">
        <div class="keyword-plan-head">
          <strong>AI 搜索关键词</strong>
          <span>食住行各 5 个，用于约束联网搜索方向</span>
        </div>
        <div class="keyword-plan-grid">
          <section v-for="group in keywordGroups" :key="group.direction" class="keyword-plan-group">
            <div class="keyword-group-title">
              <strong>{{ group.direction }}</strong>
              <span>{{ group.keywords.length }} 个</span>
            </div>
            <ul>
              <li v-for="keyword in group.keywords" :key="keyword">{{ keyword }}</li>
            </ul>
          </section>
        </div>
      </div>
      <div class="research-links" v-if="researchSources.length">
        <a
          v-for="source in researchSources"
          :key="source.url + source.title"
          :href="source.url"
          target="_blank"
          rel="noreferrer"
          :title="source.snippet"
        >
          {{ source.platform }} · {{ source.title }}
        </a>
      </div>
    </div>

    <div v-if="routeDecision" class="route-decision">
      <div class="route-decision-head">
        <div>
          <span>路线决策</span>
          <strong>{{ routeDecisionTitle }}</strong>
        </div>
        <div class="route-score">{{ routeDecision.routeScore || 0 }}</div>
      </div>
      <p v-if="routeDecisionSummary" class="route-summary">{{ routeDecisionSummary }}</p>
      <div class="route-metrics">
        <span>{{ formatDistance(routeDecision.totalDistance) }}</span>
        <span>{{ formatTime(routeDecision.totalDriveTime || 0) }}</span>
      </div>
      <div v-if="routeDecision.optimizationNotes" class="route-notes">
        {{ routeDecision.optimizationNotes }}
      </div>
      <div v-if="routeCandidates.length" class="candidate-list">
        <article
          v-for="candidate in routeCandidates"
          :key="candidate.name"
          :class="['candidate-card', { selected: candidate.selected }]"
        >
          <div class="candidate-top">
            <strong>{{ readableCandidateName(candidate.name) }}</strong>
            <span>{{ candidate.score || 0 }}分</span>
          </div>
          <div class="candidate-meta">
            <span>{{ candidate.strategy }}</span>
            <span>{{ formatDistance(candidate.totalDistance) }}</span>
            <span>{{ formatTime(candidate.totalDriveTime || 0) }}</span>
          </div>
          <p>{{ readableCandidateTradeOff(candidate.tradeOff) }}</p>
          <small>{{ readableCandidateSummary(candidate.daysSummary) }}</small>
        </article>
      </div>
    </div>

    <div v-if="tripOptions.length" class="option-strip">
      <div class="option-title">
        <span>玩法对比</span>
        <strong>{{ tripOptions.length }} 种方案</strong>
      </div>
      <div class="option-grid">
        <article v-for="option in tripOptions" :key="option.name" class="option-card">
          <div class="option-head">
            <strong>{{ option.name }}</strong>
            <span>{{ option.style }}</span>
          </div>
          <p>{{ option.summary }}</p>
          <small>{{ option.tradeOff }}</small>
          <div class="option-pois">{{ option.poiNames }}</div>
        </article>
      </div>
    </div>

    <div class="day-tabs">
      <button
        v-for="day in plan.days"
        :key="day.day"
        :class="['day-tab', { active: selectedDay === day.day }]"
        :style="selectedDay === day.day ? { background: safeColor(day.day - 1), borderColor: safeColor(day.day - 1) } : {}"
        @click="$emit('day-select', day.day)"
      >
        <span class="tab-num">D{{ day.day }}</span>
        <span class="tab-theme">{{ day.theme || '第' + day.day + '天' }}</span>
      </button>
    </div>

    <!-- 当日详情 -->
    <div class="day-detail" v-if="currentDay">
      <div class="detail-header">
        <span class="detail-badge" :style="{ background: safeColor(currentDay.day - 1) }">
          Day {{ currentDay.day }}
        </span>
        <span class="detail-theme">{{ currentDay.theme }}</span>
      </div>

      <div class="stats-row">
        <div class="stat-item">
          <span class="stat-icon">📍</span>
          <span>{{ currentDay.pois?.length || 0 }} 个景点</span>
        </div>
        <div class="stat-item">
          <span class="stat-icon">📏</span>
          <span>{{ currentDay.distance || 0 }} km</span>
        </div>
        <div class="stat-item">
          <span class="stat-icon">⏱️</span>
          <span>{{ formatTime(currentDay.driveTime ?? currentDay.drive_time ?? 0) }}</span>
        </div>
      </div>

      <div v-if="hasFullSchedule(currentDay)" class="schedule-panel">
        <div class="schedule-title">
          <strong>全天安排</strong>
          <span>从早到晚覆盖衣食住行</span>
        </div>
        <div class="schedule-timeline">
          <div
            v-for="(item, index) in currentDay.schedule"
            :key="item.timeRange + item.title + index"
            class="schedule-item"
          >
            <div class="schedule-marker" :style="{ background: safeColor(currentDay.day - 1) }">
              <span>{{ index + 1 }}</span>
            </div>
            <div class="schedule-content">
              <div class="schedule-head">
                <strong>{{ item.timeRange || item.period }}</strong>
                <span class="schedule-tag">{{ item.type }}</span>
              </div>
              <div class="schedule-body">
                <div class="schedule-title-main">{{ item.title }}</div>
                <div v-if="item.location" class="schedule-location">📍 {{ item.location }}</div>
                <div v-if="item.description" class="schedule-desc">{{ item.description }}</div>
                <div v-if="item.transport" class="schedule-meta">🚗 {{ item.transport }}</div>
                <div v-if="item.costHint" class="schedule-meta">💰 {{ item.costHint }}</div>
                <div v-if="item.tips" class="schedule-tips">提示：{{ item.tips }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div v-if="currentDay.options?.length" class="day-options">
        <div v-for="option in currentDay.options" :key="option.name" class="day-option">
          <strong>{{ option.name }}</strong>
          <span>{{ option.summary }}</span>
        </div>
      </div>

      <div v-if="currentDay.stay || currentDay.clothingTips || currentDay.dailyTransport || currentDay.budgetHint" class="day-brief">
        <div v-if="currentDay.stay" class="brief-block">
          <strong>住宿建议</strong>
          <p>{{ currentDay.stay.area }} · {{ currentDay.stay.hotelType }}</p>
          <p v-if="currentDay.stay.reason">{{ currentDay.stay.reason }}</p>
          <p v-if="currentDay.stay.checkInTip">{{ currentDay.stay.checkInTip }}</p>
        </div>
        <div v-if="currentDay.dailyTransport" class="brief-block">
          <strong>交通建议</strong>
          <p>{{ currentDay.dailyTransport }}</p>
        </div>
        <div v-if="currentDay.clothingTips" class="brief-block">
          <strong>穿衣建议</strong>
          <p>{{ currentDay.clothingTips }}</p>
        </div>
        <div v-if="currentDay.budgetHint" class="brief-block">
          <strong>预算提示</strong>
          <p>{{ currentDay.budgetHint }}</p>
        </div>
      </div>

      <!-- 景点时间线 -->
      <div class="poi-timeline">
        <div
          v-for="(poi, index) in currentDay.pois"
          :key="poi.name"
          class="timeline-item"
        >
          <div class="timeline-marker" :style="{ background: safeColor(currentDay.day - 1) }">
            <span>{{ index + 1 }}</span>
          </div>
          <div class="timeline-content">
            <div class="poi-card">
              <img
                v-if="poi.imageUrl"
                class="poi-image"
                :src="poi.imageUrl"
                :alt="poi.name"
                loading="lazy"
                @error="hideBrokenImage"
              />
              <div class="poi-main">
                <div class="poi-name">{{ poi.name }}</div>
                <div v-if="poi.tags" class="poi-tags">
                  <span v-for="tag in splitTags(poi.tags)" :key="tag">{{ tag }}</span>
                </div>
              </div>
            </div>
            <div class="poi-meta">
              <span v-if="poi.stayTime">🕐 {{ formatTime(poi.stayTime) }}</span>
              <span v-if="poi.lat && poi.lng">
                📍 {{ poi.lat.toFixed(4) }}, {{ poi.lng.toFixed(4) }}
              </span>
            </div>
            <div class="poi-reason" v-if="poi.reason">{{ poi.reason }}</div>
            <div class="poi-desc" v-if="poi.description">{{ poi.description }}</div>
            <div class="poi-tips" v-if="poi.tips">{{ poi.tips }}</div>
            <!-- 景点间连线 -->
            <div
              v-if="index < currentDay.pois.length - 1"
              class="poi-connector"
              :style="{ borderColor: safeColor(currentDay.day - 1) }"
            >
              <span class="connector-label">
                ↓ {{ segmentLabel(currentDay, index) }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { haversine, formatTime } from '../utils/geo.js'

const props = defineProps({
  plan: Object,
  selectedDay: Number,
  dayColors: Array
})

defineEmits(['day-select'])

// 安全获取颜色（循环取模，防止越界）
function safeColor(index) {
  if (!props.dayColors || props.dayColors.length === 0) return '#4A90D9'
  return props.dayColors[index % props.dayColors.length]
}

const currentDay = computed(() => {
  if (!props.plan?.days) return null
  return props.plan.days.find(d => d.day === props.selectedDay) || null
})

const researchStatusText = computed(() => {
  const status = props.plan?.researchReport?.status
  if (status === 'ok') return '实时来源'
  if (status === 'rag_public_search') return '公开搜索 RAG'
  if (status === 'entry_only') return '仅搜索入口'
  if (status === 'no_verified_sources') return '无可信来源'
  return status || '已启用'
})

const researchSources = computed(() => {
  return (props.plan?.researchReport?.sources || []).slice(0, 4)
})

const keywordGroups = computed(() => {
  const groups = props.plan?.researchReport?.keywordGroups || {}
  return ['食', '住', '行']
    .map(direction => ({
      direction,
      keywords: Array.isArray(groups[direction])
        ? groups[direction].filter(Boolean).slice(0, 5)
        : []
    }))
    .filter(group => group.keywords.length > 0)
})

const totalKeywordCount = computed(() => {
  return keywordGroups.value.reduce((total, group) => total + group.keywords.length, 0)
})

const tripOptions = computed(() => {
  return props.plan?.options || []
})

const routeDecision = computed(() => {
  return props.plan?.routeDecisionReport || props.plan?.route_decision_report || null
})

const isDataGuardDecision = computed(() => {
  const strategy = routeDecision.value?.selectedStrategy || ''
  return strategy.includes('数据不足') || strategy.includes('地图点位')
})

const routeDecisionTitle = computed(() => {
  if (isDataGuardDecision.value) {
    return '地图点位未确认'
  }
  return routeDecision.value?.selectedStrategy || '聚类顺路优化'
})

const routeDecisionSummary = computed(() => {
  if (isDataGuardDecision.value) {
    return '已经完成联网调研和搜索关键词生成，但当前没有拿到足够可靠的地图坐标。系统会保留调研结果和全天安排提示，不会把出发地或旧目的地景点混进路线里。'
  }
  return routeDecision.value?.summary || ''
})

const routeCandidates = computed(() => {
  return routeDecision.value?.candidates || []
})

function formatDistance(distance) {
  const value = Number(distance || 0)
  return `${value.toFixed(1)} km`
}

function hasFullSchedule(day) {
  return Array.isArray(day?.schedule) && day.schedule.length > 0
}

function getDistanceBetween(pois, index) {
  if (!pois || index >= pois.length - 1) return ''
  const a = pois[index]
  const b = pois[index + 1]
  if (a.lat && a.lng && b.lat && b.lng) {
    const dist = haversine(a.lat, a.lng, b.lat, b.lng)
    return dist.toFixed(1) + ' km'
  }
  return ''
}

function segmentLabel(day, index) {
  const segment = day?.segments?.[index]
  if (segment) {
    const distance = segment.distance ? `${Number(segment.distance).toFixed(1)} km` : '距离待估算'
    const driveTime = segment.driveTime ?? segment.drive_time
    const time = driveTime ? formatTime(driveTime) : '时间待估算'
    return `${segment.from || day.pois[index]?.name} → ${segment.to || day.pois[index + 1]?.name} · ${distance} · ${time}`
  }
  return getDistanceBetween(day?.pois, index) || '未知距离'
}

function splitTags(tags) {
  return String(tags || '')
    .split(/[、,，]/)
    .map(tag => tag.trim())
    .filter(Boolean)
    .slice(0, 4)
}

function hideBrokenImage(event) {
  event.target.style.display = 'none'
}

function readableCandidateName(name) {
  if (String(name || '').includes('数据不足')) {
    return '暂不绘制地图路线'
  }
  return name
}

function readableCandidateTradeOff(text) {
  if (String(text || '').includes('不可验证')) {
    return '为了避免给出错误坐标，系统暂时不绘制地图路线；请保留当前调研结果，补充更具体目的地或重新规划。'
  }
  return text
}

function readableCandidateSummary(text) {
  if (String(text || '').includes('可验证 POI')) {
    return '当前没有足够可靠的目的地坐标点。'
  }
  return text
}
</script>

<style scoped>
.trip-list {
  border-top: 1px solid var(--border);
}

.list-header {
  padding: 16px 24px 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.list-header h3 {
  font-size: 16px;
  font-weight: 600;
}

.day-count {
  font-size: 13px;
  color: var(--text-light);
  background: #f0f0f0;
  padding: 3px 10px;
  border-radius: 12px;
}

.research-strip {
  margin: 0 24px 14px;
  padding: 10px 12px;
  border: 1px solid var(--color-border, #e2e8f0);
  border-radius: 8px;
  background: var(--color-bg-alt, #f8fafc);
}

.research-title {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  font-size: 12px;
  color: var(--color-text-secondary, #475569);
  margin-bottom: 8px;
}

.research-title strong {
  color: var(--color-primary-dark, #0369a1);
}

.research-summary {
  margin: 0 0 8px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--color-text-secondary, #475569);
}

.research-counts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}

.research-counts span {
  padding: 2px 8px;
  border-radius: 999px;
  background: white;
  border: 1px solid var(--color-border, #e2e8f0);
  color: var(--color-text-muted, #64748b);
  font-size: 11px;
}

.keyword-plan {
  margin: 8px 0 10px;
  padding: 10px;
  border-radius: 8px;
  background: white;
  border: 1px solid var(--color-border-light, #f1f5f9);
}

.keyword-plan-head {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
  margin-bottom: 10px;
}

.keyword-plan-head strong {
  font-size: 13px;
  color: var(--color-text, #0f172a);
}

.keyword-plan-head span {
  font-size: 11px;
  color: var(--color-text-muted, #64748b);
  text-align: right;
}

.keyword-plan-grid {
  display: grid;
  gap: 8px;
}

.keyword-plan-group {
  padding: 8px;
  border-radius: 8px;
  background: var(--color-bg-alt, #f8fafc);
}

.keyword-group-title {
  display: flex;
  justify-content: space-between;
  margin-bottom: 6px;
  font-size: 12px;
}

.keyword-group-title strong {
  color: var(--color-primary-dark, #0369a1);
}

.keyword-group-title span {
  color: var(--color-text-muted, #64748b);
}

.keyword-plan-group ul {
  margin: 0;
  padding-left: 16px;
  display: grid;
  gap: 4px;
}

.keyword-plan-group li {
  font-size: 12px;
  line-height: 1.45;
  color: var(--color-text-secondary, #475569);
  word-break: break-word;
}

.research-links {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.research-links a {
  color: var(--color-primary-dark, #0369a1);
  font-size: 12px;
  line-height: 1.4;
  text-decoration: none;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.research-links a:hover {
  text-decoration: underline;
}

.route-decision {
  margin: 0 24px 14px;
  padding: 12px;
  border: 1px solid rgba(14, 165, 233, 0.22);
  border-radius: 8px;
  background: #f8fafc;
}

.route-decision-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  margin-bottom: 8px;
}

.route-decision-head div:first-child {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.route-decision-head span {
  font-size: 12px;
  color: var(--color-text-muted, #64748b);
}

.route-decision-head strong {
  font-size: 14px;
  color: var(--color-text, #0f172a);
}

.route-score {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: #0ea5e9;
  color: white;
  font-size: 15px;
  font-weight: 700;
}

.route-summary,
.route-notes {
  margin: 0 0 8px;
  color: var(--color-text-secondary, #475569);
  font-size: 12px;
  line-height: 1.5;
}

.route-metrics {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.route-metrics span {
  padding: 3px 8px;
  border-radius: 999px;
  background: white;
  border: 1px solid var(--color-border, #e2e8f0);
  color: var(--color-primary-dark, #0369a1);
  font-size: 12px;
}

.candidate-list {
  display: grid;
  gap: 8px;
  max-height: 260px;
  overflow-y: auto;
  padding-right: 2px;
}

.candidate-card {
  padding: 10px;
  border-radius: 8px;
  border: 1px solid var(--color-border-light, #f1f5f9);
  background: white;
}

.candidate-card.selected {
  border-color: #0ea5e9;
  box-shadow: inset 3px 0 0 #0ea5e9;
}

.candidate-top,
.candidate-meta {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: center;
}

.candidate-top strong {
  min-width: 0;
  color: var(--color-text, #0f172a);
  font-size: 13px;
}

.candidate-top span {
  flex-shrink: 0;
  color: #0284c7;
  font-size: 12px;
  font-weight: 700;
}

.candidate-meta {
  justify-content: flex-start;
  flex-wrap: wrap;
  margin: 6px 0;
}

.candidate-meta span {
  color: var(--color-text-muted, #64748b);
  font-size: 11px;
}

.candidate-card p,
.candidate-card small {
  display: block;
  margin: 0;
  color: var(--color-text-secondary, #475569);
  font-size: 12px;
  line-height: 1.45;
}

.candidate-card small {
  margin-top: 5px;
  color: var(--color-text-muted, #64748b);
}

.option-strip {
  margin: 0 24px 14px;
  padding: 12px;
  border: 1px solid var(--color-border, #e2e8f0);
  border-radius: 8px;
  background: #fff;
}

.option-title {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
  font-size: 12px;
  color: var(--color-text-secondary, #475569);
}

.option-title strong {
  color: var(--color-accent, #f97316);
}

.option-grid {
  display: grid;
  gap: 8px;
}

.option-card {
  padding: 10px;
  border: 1px solid var(--color-border-light, #f1f5f9);
  border-radius: 8px;
  background: var(--color-bg-alt, #f8fafc);
}

.option-head {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
}

.option-head strong {
  font-size: 13px;
  color: var(--color-text, #0c4a6e);
}

.option-head span {
  flex-shrink: 0;
  font-size: 11px;
  color: var(--color-primary-dark, #0369a1);
}

.option-card p,
.option-card small {
  display: block;
  margin: 0 0 5px;
  font-size: 12px;
  line-height: 1.45;
  color: var(--color-text-secondary, #475569);
}

.option-pois {
  font-size: 11px;
  line-height: 1.45;
  color: var(--color-text-muted, #64748b);
}

/* 日期标签 */
.day-tabs {
  display: flex;
  gap: 6px;
  padding: 0 24px 16px;
  overflow-x: auto;
}

.day-tab {
  flex-shrink: 0;
  padding: 8px 16px;
  border: 1.5px solid var(--border);
  border-radius: 20px;
  background: white;
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.day-tab:hover {
  border-color: var(--primary);
}

.day-tab.active {
  color: white;
}

.tab-num {
  font-size: 13px;
  font-weight: 700;
}

.tab-theme {
  font-size: 11px;
  opacity: 0.85;
  max-width: 60px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 当日详情 */
.day-detail {
  padding: 0 24px 20px;
}

.detail-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.detail-badge {
  padding: 4px 12px;
  border-radius: 12px;
  color: white;
  font-size: 13px;
  font-weight: 600;
}

.detail-theme {
  font-size: 15px;
  font-weight: 500;
}

.stats-row {
  display: flex;
  gap: 16px;
  margin-bottom: 18px;
  padding: 10px 14px;
  background: #f8f9fa;
  border-radius: 8px;
}

.day-options {
  display: grid;
  gap: 8px;
  margin-bottom: 16px;
}

.day-option {
  padding: 9px 10px;
  border-radius: 8px;
  background: var(--color-accent-light, #fff7ed);
  border: 1px solid rgba(249, 115, 22, 0.18);
}

.day-option strong {
  display: block;
  margin-bottom: 3px;
  font-size: 12px;
  color: var(--color-accent-hover, #ea580c);
}

.day-option span {
  font-size: 12px;
  line-height: 1.45;
  color: var(--color-text-secondary, #475569);
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--text-light);
}

.stat-icon {
  font-size: 14px;
}

.schedule-panel {
  margin-bottom: 16px;
  border: 1px solid var(--color-border, #e2e8f0);
  border-radius: 8px;
  background: #fff;
  overflow: hidden;
}

.schedule-title {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
  padding: 12px 14px;
  border-bottom: 1px solid var(--color-border-light, #f1f5f9);
  background: var(--color-bg-alt, #f8fafc);
}

.schedule-title strong {
  font-size: 14px;
  color: var(--color-text, #0f172a);
}

.schedule-title span {
  font-size: 12px;
  color: var(--color-text-muted, #64748b);
}

.schedule-timeline {
  max-height: 420px;
  overflow-y: auto;
  padding: 12px 14px 4px;
}

.schedule-timeline::-webkit-scrollbar {
  width: 6px;
}

.schedule-timeline::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 999px;
}

.schedule-item {
  display: grid;
  grid-template-columns: 26px minmax(0, 1fr);
  gap: 10px;
  position: relative;
}

.schedule-item:not(:last-child)::before {
  content: '';
  position: absolute;
  left: 12px;
  top: 26px;
  bottom: 0;
  border-left: 1px dashed var(--color-border, #e2e8f0);
}

.schedule-marker {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  z-index: 1;
}

.schedule-content {
  padding-bottom: 12px;
  min-width: 0;
}

.schedule-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.schedule-head strong {
  font-size: 12px;
  color: var(--color-primary-dark, #0369a1);
}

.schedule-tag {
  flex-shrink: 0;
  padding: 2px 7px;
  border-radius: 999px;
  background: var(--color-primary-light, #e0f2fe);
  color: var(--color-primary-dark, #0369a1);
  font-size: 11px;
}

.schedule-body {
  padding: 10px;
  border: 1px solid var(--color-border-light, #f1f5f9);
  border-radius: 8px;
  background: #fff;
}

.schedule-title-main {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text, #0f172a);
  margin-bottom: 5px;
}

.schedule-location,
.schedule-meta {
  font-size: 12px;
  line-height: 1.45;
  color: var(--color-text-muted, #64748b);
  margin-top: 4px;
}

.schedule-desc {
  font-size: 12px;
  line-height: 1.55;
  color: var(--color-text-secondary, #475569);
  margin-top: 6px;
}

.schedule-tips {
  margin-top: 7px;
  padding: 7px 9px;
  border-radius: 7px;
  background: var(--color-warning-light, #fef3c7);
  color: #92400e;
  font-size: 12px;
  line-height: 1.45;
}

.day-brief {
  display: grid;
  gap: 8px;
  margin-bottom: 16px;
}

.brief-block {
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid var(--color-border-light, #f1f5f9);
  background: var(--color-bg-alt, #f8fafc);
}

.brief-block strong {
  display: block;
  margin-bottom: 5px;
  font-size: 12px;
  color: var(--color-text, #0f172a);
}

.brief-block p {
  margin: 3px 0 0;
  font-size: 12px;
  line-height: 1.45;
  color: var(--color-text-secondary, #475569);
}

/* 景点时间线 */
.poi-timeline {
  position: relative;
}

.timeline-item {
  display: flex;
  gap: 14px;
  position: relative;
}

.timeline-marker {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
  z-index: 1;
}

.timeline-content {
  flex: 1;
  padding-bottom: 16px;
  min-width: 0;
}

.poi-card {
  display: grid;
  grid-template-columns: 78px minmax(0, 1fr);
  gap: 10px;
  align-items: start;
  margin-bottom: 7px;
}

.poi-image {
  width: 78px;
  aspect-ratio: 4 / 3;
  object-fit: cover;
  border-radius: 8px;
  background: var(--color-bg-alt, #f8fafc);
  border: 1px solid var(--color-border-light, #f1f5f9);
}

.poi-main {
  min-width: 0;
}

.poi-name {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 4px;
}

.poi-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.poi-tags span {
  padding: 2px 6px;
  border-radius: 999px;
  background: var(--color-primary-light, #e0f2fe);
  color: var(--color-primary-dark, #0369a1);
  font-size: 11px;
  line-height: 1.2;
}

.poi-meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: var(--text-light);
  margin-bottom: 4px;
}

.poi-desc {
  font-size: 13px;
  color: #666;
  line-height: 1.5;
  background: #f8f9fa;
  padding: 6px 10px;
  border-radius: 6px;
  margin-top: 6px;
}

.poi-reason,
.poi-tips {
  font-size: 12px;
  line-height: 1.45;
  margin-top: 6px;
  padding: 7px 9px;
  border-radius: 7px;
}

.poi-reason {
  color: var(--color-text-secondary, #475569);
  background: var(--color-primary-light, #e0f2fe);
}

.poi-tips {
  color: #92400e;
  background: var(--color-warning-light, #fef3c7);
}

.poi-connector {
  border-left: 2px dashed;
  padding: 8px 0 8px 12px;
  margin: 4px 0;
}

.connector-label {
  font-size: 12px;
  color: var(--text-light);
}

.day-tabs::-webkit-scrollbar {
  height: 4px;
}

.day-tabs::-webkit-scrollbar-thumb {
  background: #ccc;
  border-radius: 2px;
}
</style>
