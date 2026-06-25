<template>
  <div class="app-shell">
    <!-- 顶部导航 - 玻璃态 -->
    <header class="app-navbar">
      <div class="navbar-inner">
        <div class="brand">
          <IconMap size="22" color="var(--color-primary)" :stroke-width="2" />
          <span class="brand-text">AI 旅行路线规划</span>
        </div>
        <div class="navbar-right">
          <div class="status-dot" :class="{ online: !loading }"></div>
          <span class="status-text">{{ loading ? '规划中' : '就绪' }}</span>
        </div>
      </div>
    </header>

    <!-- 主区域：地图全幅背景 + 浮动侧边栏 -->
    <div class="app-stage">
      <!-- 地图 - 全幅 -->
      <div class="map-backdrop">
        <MapView
          :plan="planData"
          :selected-day="selectedDay"
          :day-colors="dayColors"
          @day-select="handleDaySelect"
          @route-rendered="handleRouteRendered"
        />
      </div>

      <!-- 浮动侧边栏 - 玻璃态 -->
      <aside class="float-sidebar" :class="{ collapsed: sidebarCollapsed }">
        <!-- 折叠按钮 -->
        <button
          class="sidebar-toggle"
          @click="sidebarCollapsed = !sidebarCollapsed"
          :aria-label="sidebarCollapsed ? '展开侧边栏' : '收起侧边栏'"
        >
          <IconChevronRight size="18" />
        </button>

        <div class="sidebar-workbench" v-show="!sidebarCollapsed">
          <div class="workbench-tabs" role="tablist" aria-label="旅行规划工作台">
            <button
              v-for="tab in tabs"
              :key="tab.key"
              type="button"
              :class="['workbench-tab', { active: activeTab === tab.key }]"
              @click="activeTab = tab.key"
            >
              <component :is="tab.icon" size="15" />
              <span>{{ tab.label }}</span>
              <small v-if="tab.key === 'trip' && planData">{{ planData.days?.length || 0 }}</small>
            </button>
          </div>

          <div class="workbench-panel">
            <section v-show="activeTab === 'plan'" class="panel-scroll">
              <TravelForm
                @plan-generated="handlePlanGenerated"
                @research-preview="handleResearchPreview"
                :loading="loading"
              />
            </section>

            <section v-show="activeTab === 'trip'" class="panel-scroll">
              <TripList
                v-if="planData"
                :plan="planData"
                :selected-day="selectedDay"
                @day-select="handleDaySelect"
                :day-colors="dayColors"
              />
              <div v-else class="empty-panel">
                <IconRoute size="34" />
                <h3>还没有路线</h3>
                <p>先规划一条新旅程，或从历史记录加载已有路线。</p>
                <button type="button" @click="activeTab = 'plan'">去规划</button>
              </div>
            </section>

            <section v-show="activeTab === 'history'" class="panel-scroll">
              <HistoryPanel ref="historyPanelRef" @load-plan="handleLoadHistory" />
            </section>
          </div>
        </div>
      </aside>

      <!-- 顶栏提示（侧栏折叠时可见） -->
      <div v-if="sidebarCollapsed && planData" class="float-quickbar">
        <div
          v-for="day in planData.days"
          :key="day.day"
          :class="['quick-day', { active: selectedDay === day.day }]"
          :style="selectedDay === day.day ? { background: safeDayColor(day.day - 1), borderColor: safeDayColor(day.day - 1) } : {}"
          @click="handleDaySelect(day.day)"
        >
          D{{ day.day }}
        </div>
      </div>
    </div>

    <!-- 加载遮罩 -->
    <Transition name="fade">
      <div v-if="planningVisible" class="planning-screen">
        <div class="planning-card">
          <div class="planning-header">
            <div class="planning-icon">
              <IconSparkles size="28" color="var(--color-primary)" :stroke-width="1.6" />
            </div>
            <div>
              <h2>{{ planningTitle }}</h2>
              <p>{{ planningSubtitle }}</p>
            </div>
          </div>

          <div class="planning-progress">
            <div class="planning-progress-meta">
              <span>{{ planningProgress }}%</span>
              <strong>{{ activePlanningStep?.title || '准备中' }}</strong>
            </div>
            <div class="progress-track">
              <div class="progress-fill" :style="{ width: planningProgress + '%' }"></div>
            </div>
          </div>

          <div class="planning-timeline" aria-label="AI 规划进度">
            <div
              v-for="step in planningSteps"
              :key="step.key"
              :class="['planning-step', step.status]"
            >
              <div class="step-node">
                <IconCheckCircle v-if="step.status === 'done'" size="15" />
                <span v-else-if="step.status === 'active'" class="step-spinner"></span>
                <span v-else class="step-dot"></span>
              </div>
              <div class="step-body">
                <div class="step-title-row">
                  <strong>{{ step.title }}</strong>
                  <span>{{ stepStatusText(step.status) }}</span>
                </div>
                <p>{{ step.detail }}</p>
                <div v-if="step.keywordGroups?.length" class="step-keywords">
                  <div v-for="group in step.keywordGroups" :key="group.direction" class="keyword-group">
                    <span>{{ group.direction }}</span>
                    <em>{{ group.keywords.join('、') }}</em>
                  </div>
                </div>
                <div v-if="step.metrics?.length" class="step-metrics">
                  <span v-for="metric in step.metrics" :key="metric.label">
                    {{ metric.label }} {{ metric.value }}
                  </span>
                </div>
                <div v-if="step.logs?.length" class="step-logs">
                  <details
                    v-for="log in step.logs"
                    :key="log.key"
                    class="trace-card"
                    :open="log.open"
                  >
                    <summary>
                      <span>{{ log.title }}</span>
                      <em>{{ log.badge }}</em>
                    </summary>
                    <div class="trace-content">
                      <p v-if="log.message">{{ log.message }}</p>
                      <dl v-if="log.meta?.length" class="trace-meta">
                        <template v-for="item in log.meta" :key="item.label">
                          <dt>{{ item.label }}</dt>
                          <dd>{{ item.value }}</dd>
                        </template>
                      </dl>
                      <div v-if="log.rawSources?.length" class="trace-source-block">
                        <strong>原始搜索结果与清洗判断</strong>
                        <ol>
                          <li v-for="source in log.rawSources" :key="source.url || source.title">
                            <span>
                              {{ source.title }}
                              <em :class="['source-status', source.retained ? 'kept' : source.cleanStatus || 'candidate']">
                                {{ sourceStatusText(source) }}
                              </em>
                            </span>
                            <small>{{ source.platform }}｜{{ source.evidenceType }}｜{{ source.url }}</small>
                            <small v-if="source.rejectReason" class="reject-reason">过滤原因：{{ source.rejectReason }}</small>
                            <small v-else-if="source.snippet">摘要：{{ source.snippet }}</small>
                          </li>
                        </ol>
                      </div>
                      <div v-if="log.cleanedSources?.length" class="trace-source-block verified">
                        <strong>清洗后保留</strong>
                        <ol>
                          <li v-for="source in log.cleanedSources" :key="source.url || source.title">
                            <span>{{ source.title }}</span>
                            <small>评分 {{ source.score || 0 }}｜{{ source.platform }}｜{{ source.url }}</small>
                          </li>
                        </ol>
                      </div>
                    </div>
                  </details>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import TravelForm from './components/TravelForm.vue'
import TripList from './components/TripList.vue'
import MapView from './components/MapView.vue'
import HistoryPanel from './components/HistoryPanel.vue'
import { IconMap, IconChevronRight, IconSparkles, IconHistory, IconRoute, IconSearch, IconCheckCircle } from './icons/index.js'

const loading = ref(false)
const planningVisible = ref(false)
const planData = ref(null)
const selectedDay = ref(1)
const sidebarCollapsed = ref(false)
const activeTab = ref('plan')
const historyPanelRef = ref(null)
const planningIndex = ref(0)
const planningProgress = ref(0)
const researchPreview = ref(null)
let planningTimer = null
let planningFinishFallbackTimer = null
let planningStartedAt = 0

const tabs = [
  { key: 'plan', label: '规划', icon: IconSearch },
  { key: 'trip', label: '行程', icon: IconRoute },
  { key: 'history', label: '历史', icon: IconHistory }
]

// 按天颜色方案
const dayColors = [
  '#EF4444', '#F97316', '#EAB308', '#22C55E', '#14B8A6',
  '#0EA5E9', '#6366F1', '#A855F7', '#EC4899', '#64748B'
]

const planningStepTemplates = [
  {
    key: 'request',
    title: '接收旅行需求',
    detail: '读取出发地、目的地、天数、偏好和补充要求。'
  },
  {
    key: 'keywords',
    title: 'AI 生成搜索关键词',
    detail: '围绕目的地、省市、偏好和补充要求生成食、住、行三组关键词。'
  },
  {
    key: 'research',
    title: '多源联网检索',
    detail: '按 15 个关键词执行多源搜索，每个关键词最多保留 20 条有效结果。'
  },
  {
    key: 'rag',
    title: '清洗结果并构建 RAG 证据',
    detail: '去重、过滤低质量结果、按真实性和相关性打分。'
  },
  {
    key: 'reasoning',
    title: 'AI Agent 推理路线',
    detail: '结合 RAG 证据、用户偏好和历史记忆进行路线取舍。'
  },
  {
    key: 'poi',
    title: '验证 POI 与坐标',
    detail: '通过 POI 工具校验景点名称、经纬度、停留时间和路程。'
  },
  {
    key: 'route',
    title: '生成结构化行程',
    detail: '整理每日主题、景点顺序、距离、车程和注意事项。'
  },
  {
    key: 'map',
    title: '绘制到地图',
    detail: '在高德地图上绘制 marker、polyline、图例和每日路线高亮。'
  }
]

const planningThresholds = [0, 1800, 4200, 7600, 14000, 26000, 60000]

const planningSteps = computed(() => {
  return planningStepTemplates.map((step, index) => {
    const enhanced = enhancePlanningStep(step)
    return {
      ...enhanced,
      status: index < planningIndex.value
        ? 'done'
        : index === planningIndex.value
          ? 'active'
          : 'pending'
    }
  })
})

const activePlanningStep = computed(() => planningSteps.value[planningIndex.value])

const planningTitle = computed(() => {
  if (planningIndex.value >= planningStepTemplates.length - 1 && planningProgress.value >= 96) {
    return '正在把路线放到地图上'
  }
  return 'AI 正在规划路线'
})

const planningSubtitle = computed(() => {
  if (planningProgress.value >= 100) return '路线已生成，地图展示完成'
  return '每一步的关键词、搜索结果、清洗证据和运行内容都会在这里展开。'
})

onBeforeUnmount(() => {
  clearPlanningTimer()
  clearPlanningFinishFallbackTimer()
})

function safeDayColor(index) {
  if (!dayColors || dayColors.length === 0) return 'var(--color-primary)'
  return dayColors[index % dayColors.length]
}

function handlePlanGenerated(data) {
  if (data === null) {
    loading.value = true
    startPlanningProgress()
  } else {
    loading.value = false
    if (!data?.days?.length) {
      clearPlanningTimer()
      planningVisible.value = false
      planData.value = data
      return
    }
    planData.value = data
    selectedDay.value = 1
    sidebarCollapsed.value = false
    activeTab.value = 'trip'
    movePlanningToMapStage()
    refreshHistoryPanel()
  }
}

function handleResearchPreview(report) {
  researchPreview.value = report
  planningIndex.value = Math.max(planningIndex.value, 2)
  planningProgress.value = Math.max(planningProgress.value, 34)
}

function handleDaySelect(day) {
  selectedDay.value = day
}

function handleLoadHistory(data) {
  planData.value = data
  selectedDay.value = 1
  sidebarCollapsed.value = false
  activeTab.value = 'trip'
}

function refreshHistoryPanel() {
  const panel = historyPanelRef.value
  if (panel?.reloadHistory) {
    panel.reloadHistory()
  }
}

function startPlanningProgress() {
  planData.value = null
  researchPreview.value = null
  planningVisible.value = true
  planningIndex.value = 0
  planningProgress.value = 4
  planningStartedAt = Date.now()
  clearPlanningTimer()
  clearPlanningFinishFallbackTimer()

  planningTimer = window.setInterval(() => {
    const elapsed = Date.now() - planningStartedAt
    const nextIndex = planningThresholds.reduce((current, threshold, index) => {
      return elapsed >= threshold ? index : current
    }, 0)
    planningIndex.value = Math.min(nextIndex, planningStepTemplates.length - 2)

    const estimatedProgress = Math.min(88, 6 + Math.floor(elapsed / 900))
    planningProgress.value = Math.max(planningProgress.value, estimatedProgress)
  }, 700)
}

function movePlanningToMapStage() {
  clearPlanningTimer()
  clearPlanningFinishFallbackTimer()
  planningVisible.value = true
  planningIndex.value = planningStepTemplates.length - 1
  planningProgress.value = Math.max(planningProgress.value, 92)

  planningFinishFallbackTimer = window.setTimeout(() => {
    const hasRenderableRoute = planData.value?.days?.some(day => {
      return day.pois?.some(poi => poi?.lat && poi?.lng)
    })
    if (planningVisible.value && hasRenderableRoute) {
      handleRouteRendered({ hasRoute: true, source: 'fallback' })
    }
  }, 3500)

  const hasRenderableRoute = planData.value?.days?.some(day => {
    return day.pois?.some(poi => poi?.lat && poi?.lng)
  })
  if (!hasRenderableRoute) {
    window.setTimeout(() => {
      if (!planningVisible.value) return
      planningIndex.value = planningStepTemplates.length
      planningProgress.value = 100
      clearPlanningFinishFallbackTimer()
      window.setTimeout(() => {
        planningVisible.value = false
      }, 1100)
    }, 1200)
  }
}

function handleRouteRendered(payload) {
  const hasRenderableRoute = planData.value?.days?.some(day => {
    return day.pois?.some(poi => poi?.lat && poi?.lng)
  })
  if (!payload?.hasRoute && !hasRenderableRoute) return
  planningIndex.value = planningStepTemplates.length
  planningProgress.value = 100
  clearPlanningTimer()
  clearPlanningFinishFallbackTimer()
  window.setTimeout(() => {
    planningVisible.value = false
  }, 1100)
}

function clearPlanningTimer() {
  if (planningTimer) {
    window.clearInterval(planningTimer)
    planningTimer = null
  }
}

function clearPlanningFinishFallbackTimer() {
  if (planningFinishFallbackTimer) {
    window.clearTimeout(planningFinishFallbackTimer)
    planningFinishFallbackTimer = null
  }
}

function stepStatusText(status) {
  if (status === 'done') return '已完成'
  if (status === 'active') return '进行中'
  return '待执行'
}

function enhancePlanningStep(step) {
  const report = researchPreview.value
  if (!report) return step

  if (step.key === 'keywords') {
    const groups = normalizeKeywordGroups(report.keywordGroups)
    const totalKeywords = groups.reduce((total, group) => total + group.keywords.length, 0)
    return {
      ...step,
      detail: `已生成 ${totalKeywords || report.targetKeywordCount || 15} 个关键词：食 ${countGroup(groups, '食')} 个、住 ${countGroup(groups, '住')} 个、行 ${countGroup(groups, '行')} 个。`,
      keywordGroups: groups,
      metrics: [
        { label: '目标关键词', value: report.targetKeywordCount || 15 },
        { label: '每词结果', value: report.targetSourcesPerKeyword || 20 },
        { label: '期望有效', value: report.targetEffectiveSourceCount || 300 }
      ],
      logs: buildKeywordLogs(report)
    }
  }

  if (step.key === 'research') {
    const targetKeywords = report.targetKeywordCount || 15
    const perKeyword = report.targetSourcesPerKeyword || 20
    const targetTotal = report.targetEffectiveSourceCount || targetKeywords * perKeyword
    return {
      ...step,
      detail: `目标 ${targetKeywords} 个关键词 × 每词最多 ${perKeyword} 条，期望 ${targetTotal} 条有效结果；当前原始 ${report.rawSourceCount || 0} 条。`,
      metrics: [
        { label: '搜索批次', value: normalizeTraces(report.traces).length || targetKeywords },
        { label: '原始结果', value: report.rawSourceCount || 0 },
        { label: '清洗保留', value: report.cleanedSourceCount || 0 }
      ],
      logs: buildTraceLogs(report)
    }
  }

  if (step.key === 'rag') {
    return {
      ...step,
      detail: `状态 ${researchStatusLabel(report.status)}；清洗后保留 ${report.cleanedSourceCount || 0} 条证据，用于约束路线、餐饮、住宿和交通安排。`,
      metrics: [
        { label: '可信策略', value: report.trustPolicy ? '已启用' : '默认' },
        { label: '证据数', value: report.cleanedSourceCount || 0 }
      ],
      logs: buildRagLogs(report)
    }
  }

  if (step.key === 'reasoning') {
    return {
      ...step,
      logs: [
        {
          key: 'reasoning-policy',
          title: '推理约束',
          badge: 'RAG 约束',
          open: true,
          message: 'Agent 只允许使用清洗后的公开来源、用户输入和可验证 POI；搜索入口类结果只能作为人工复核入口，不能当作事实证据。',
          meta: [
            { label: '目的地', value: report.destination || '未返回' },
            { label: '偏好', value: report.preference || '未填写' },
            { label: '证据摘要', value: report.evidenceSummary || '暂无摘要' }
          ]
        }
      ]
    }
  }

  return step
}

function normalizeKeywordGroups(groups) {
  return ['食', '住', '行'].map(direction => ({
    direction,
    keywords: Array.isArray(groups?.[direction]) ? groups[direction].slice(0, 5) : []
  }))
}

function countGroup(groups, direction) {
  return groups.find(group => group.direction === direction)?.keywords.length || 0
}

function researchStatusLabel(status) {
  const labels = {
    ok: '已获得可验证来源',
    rag_public_search: '公开搜索增强',
    entry_only: '仅搜索入口',
    no_verified_sources: '未获得可验证来源',
    preview_failed: '调研预览失败'
  }
  return labels[status] || status || '处理中'
}

function buildKeywordLogs(report) {
  const traces = normalizeTraces(report.traces)
  if (!traces.length) return []
  return normalizeKeywordGroups(report.keywordGroups).map(group => ({
    key: `keyword-${group.direction}`,
    title: `${group.direction}方向关键词`,
    badge: `${group.keywords.length} 个`,
    open: true,
    message: `这些关键词由 AI 根据目的地、旅行偏好和补充要求生成，用于限制联网搜索范围，避免搜索到无关城市或出发地景点。`,
    meta: group.keywords.map((keyword, index) => ({
      label: `关键词 ${index + 1}`,
      value: keyword
    }))
  }))
}

function buildTraceLogs(report) {
  return normalizeTraces(report.traces).map((trace, index) => ({
    key: `trace-${trace.order || index}`,
    title: `${trace.direction || '调研'}｜${trace.keyword || '未命名关键词'}`,
    badge: `${trace.rawCount || 0} 原始 / ${trace.cleanedCount || 0} 保留`,
    open: index < 3,
    message: trace.message,
    meta: [
      { label: '搜索轮次', value: trace.round || '未标记' },
      { label: '实际查询', value: trace.executedKeyword || trace.keyword || '未返回' },
      { label: '调用工具', value: trace.provider || providerLabel(trace.platformHint) },
      { label: '状态', value: searchTraceStatusLabel(trace.status) },
      { label: '目的', value: trace.purpose || '未返回' }
    ],
    rawSources: limitSources(trace.rawSources, 20),
    cleanedSources: limitSources(trace.cleanedSources, 20)
  }))
}

function buildRagLogs(report) {
  const logs = [
    {
      key: 'rag-summary',
      title: 'RAG 证据摘要',
      badge: researchStatusLabel(report.status),
      open: true,
      message: report.evidenceSummary || '暂无可验证证据摘要。',
      meta: [
        { label: '原始来源', value: report.rawSourceCount || 0 },
        { label: '清洗证据', value: report.cleanedSourceCount || 0 },
        { label: '可信规则', value: report.trustPolicy || '未返回' }
      ]
    }
  ]
  const sources = limitSources(report.sources, 8)
  if (sources.length) {
    logs.push({
      key: 'rag-sources',
      title: '入选证据样例',
      badge: `${sources.length} 条`,
      open: true,
      message: '以下是清洗后用于约束行程规划的来源样例。',
      cleanedSources: sources
    })
  }
  return logs
}

function normalizeTraces(traces) {
  return Array.isArray(traces) ? traces : []
}

function limitSources(sources, limit) {
  return Array.isArray(sources) ? sources.slice(0, limit) : []
}

function providerLabel(platformHint) {
  const labels = {
    xhs: 'XhsSearchService',
    douyin: 'DouyinSearchService',
    web: 'WebSearchService'
  }
  return labels[platformHint] || platformHint || '未返回'
}

function searchTraceStatusLabel(status) {
  const labels = {
    ok: '成功返回公开结果',
    empty: '无结果',
    entry_only: '仅保留搜索入口',
    failed: '调用失败',
    skipped: '已跳过'
  }
  return labels[status] || status || '未知'
}

function sourceStatusText(source) {
  if (source.retained) return '已保留'
  if (source.cleanStatus === 'filtered') return '已过滤'
  if (source.cleanStatus === 'candidate') return '候选'
  return source.evidenceType === 'search_entry_only' ? '入口' : '待清洗'
}
</script>

<style>
/* === 应用外壳 === */
.app-shell {
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
  background: var(--color-bg);
}

/* === 顶部导航 === */
.app-navbar {
  position: relative;
  z-index: var(--z-sticky);
  background: var(--glass-bg-strong);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  border-bottom: 1px solid var(--color-border);
  box-shadow: var(--shadow-sm);
}

.navbar-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--space-xl);
  height: 52px;
  max-width: 100%;
}

.brand {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.brand-text {
  font-size: var(--text-lg);
  font-weight: 700;
  color: var(--color-text);
  letter-spacing: -0.01em;
}

.navbar-right {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: var(--radius-full);
  background: var(--color-text-muted);
  transition: background var(--transition-base);
}

.status-dot.online {
  background: var(--color-success);
  box-shadow: 0 0 6px rgba(16, 185, 129, 0.4);
}

.status-text {
  font-size: var(--text-xs);
  color: var(--color-text-muted);
  font-weight: 500;
}

/* === 主舞台 === */
.app-stage {
  flex: 1;
  position: relative;
  overflow: hidden;
}

/* === 全幅地图背景 === */
.map-backdrop {
  position: absolute;
  inset: 0;
  z-index: var(--z-base);
}

/* === 浮动侧边栏 === */
.float-sidebar {
  position: absolute;
  top: var(--space-lg);
  left: var(--space-lg);
  bottom: var(--space-lg);
  width: min(420px, calc(100vw - 32px));
  z-index: var(--z-dropdown);
  background: var(--glass-bg);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-xl);
  box-shadow: var(--glass-shadow);
  display: flex;
  flex-direction: column;
  transition:
    width var(--transition-slow),
    transform var(--transition-slow),
    border-radius var(--transition-slow);
}

.float-sidebar.collapsed {
  width: 48px;
  border-radius: var(--radius-full);
}

.sidebar-toggle {
  position: absolute;
  top: var(--space-md);
  right: -14px;
  width: 28px;
  height: 28px;
  border-radius: var(--radius-full);
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--color-text-secondary);
  transition: all var(--transition-fast);
  z-index: 10;
  box-shadow: var(--shadow-sm);
}

.sidebar-toggle:hover {
  background: var(--color-primary);
  color: white;
  border-color: var(--color-primary);
}

.float-sidebar.collapsed .sidebar-toggle {
  right: 10px;
  top: 10px;
}

.float-sidebar.collapsed .sidebar-toggle svg {
  transform: rotate(180deg);
}

.sidebar-workbench {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 10px;
  gap: 10px;
}

.workbench-tabs {
  flex-shrink: 0;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
  background: var(--color-surface);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-lg);
  padding: 6px;
  box-shadow: var(--shadow-xs);
}

.workbench-tab {
  min-width: 0;
  height: 38px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--color-text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-family: inherit;
  font-size: var(--text-sm);
  font-weight: 700;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.workbench-tab:hover {
  background: var(--color-surface-hover);
  color: var(--color-text);
}

.workbench-tab.active {
  background: var(--color-primary);
  color: white;
  box-shadow: 0 8px 18px rgba(14, 165, 233, 0.18);
}

.workbench-tab small {
  min-width: 18px;
  height: 18px;
  border-radius: var(--radius-full);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.2);
  font-size: var(--text-xs);
}

.workbench-panel {
  flex: 1;
  min-height: 0;
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs);
  border: 1px solid var(--color-border-light);
  overflow: hidden;
}

.panel-scroll {
  height: 100%;
  overflow-y: auto;
  overflow-x: hidden;
  scroll-behavior: smooth;
}

.empty-panel {
  min-height: 360px;
  padding: 32px 26px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  color: var(--color-text-muted);
}

.empty-panel h3 {
  margin-top: 12px;
  color: var(--color-text);
  font-size: var(--text-lg);
}

.empty-panel p {
  margin-top: 6px;
  line-height: var(--leading-relaxed);
  font-size: var(--text-sm);
}

.empty-panel button {
  margin-top: 16px;
  height: 36px;
  padding: 0 16px;
  border: none;
  border-radius: 8px;
  background: var(--color-primary);
  color: white;
  font-family: inherit;
  font-size: var(--text-sm);
  font-weight: 700;
  cursor: pointer;
}

/* === 顶栏快速选天 === */
.float-quickbar {
  position: absolute;
  top: 116px;
  left: 50%;
  transform: translateX(-50%);
  z-index: var(--z-dropdown);
  display: flex;
  gap: var(--space-xs);
  background: var(--glass-bg-strong);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-full);
  padding: var(--space-xs);
  box-shadow: var(--glass-shadow);
}

.quick-day {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-full);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--text-sm);
  font-weight: 600;
  cursor: pointer;
  border: 1.5px solid transparent;
  background: var(--color-surface);
  color: var(--color-text-secondary);
  transition: all var(--transition-fast);
}

.quick-day:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.quick-day.active {
  color: white;
  border-color: transparent;
}

/* === 规划进度屏幕 === */
.planning-screen {
  position: fixed;
  inset: 0;
  z-index: var(--z-overlay);
  background: rgba(12, 74, 110, 0.65);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.planning-card {
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  padding: 22px;
  box-shadow: var(--shadow-xl);
  width: min(920px, 100%);
  max-height: min(780px, calc(100vh - 40px));
  display: flex;
  flex-direction: column;
  gap: 18px;
  overflow: hidden;
}

.planning-header {
  display: flex;
  align-items: center;
  gap: 14px;
}

.planning-icon {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-lg);
  background: var(--color-primary-light);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  animation: float 2s ease-in-out infinite;
}

@keyframes float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-8px); }
}

.planning-header h2 {
  font-size: var(--text-xl);
  font-weight: 700;
  color: var(--color-text);
}

.planning-header p {
  margin-top: 4px;
  font-size: var(--text-sm);
  color: var(--color-text-muted);
  line-height: var(--leading-normal);
}

.planning-progress {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.planning-progress-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: var(--text-sm);
}

.planning-progress-meta span {
  font-family: var(--font-mono);
  color: var(--color-primary-dark);
  font-weight: 700;
}

.planning-progress-meta strong {
  color: var(--color-text-secondary);
  font-size: var(--text-sm);
}

.progress-track {
  height: 8px;
  background: var(--color-border-light);
  border-radius: var(--radius-full);
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--color-primary), var(--color-accent));
  border-radius: var(--radius-full);
  transition: width var(--transition-slow);
}

.planning-timeline {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 2px 6px 2px 0;
}

.planning-step {
  display: grid;
  grid-template-columns: 28px 1fr;
  gap: 10px;
  position: relative;
}

.planning-step:not(:last-child) {
  padding-bottom: 14px;
}

.planning-step:not(:last-child)::before {
  content: '';
  position: absolute;
  left: 13px;
  top: 28px;
  bottom: 0;
  width: 2px;
  background: var(--color-border-light);
}

.planning-step.done:not(:last-child)::before {
  background: var(--color-success);
}

.step-node {
  width: 28px;
  height: 28px;
  border-radius: var(--radius-full);
  border: 1px solid var(--color-border);
  background: var(--color-bg-alt);
  color: var(--color-text-muted);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1;
}

.planning-step.done .step-node {
  background: var(--color-success);
  border-color: var(--color-success);
  color: white;
}

.planning-step.active .step-node {
  background: var(--color-primary-light);
  border-color: var(--color-primary);
}

.step-spinner {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 2px solid rgba(14, 165, 233, 0.25);
  border-top-color: var(--color-primary);
  animation: spin 0.7s linear infinite;
}

.step-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--color-text-muted);
}

.step-body {
  min-width: 0;
  padding: 1px 0 0;
}

.step-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.step-title-row strong {
  font-size: var(--text-sm);
  color: var(--color-text);
}

.step-title-row span {
  flex-shrink: 0;
  font-size: var(--text-xs);
  color: var(--color-text-muted);
}

.planning-step.done .step-title-row span {
  color: var(--color-success);
}

.planning-step.active .step-title-row span {
  color: var(--color-primary-dark);
  font-weight: 700;
}

.step-body p {
  margin-top: 3px;
  font-size: var(--text-xs);
  line-height: var(--leading-relaxed);
  color: var(--color-text-secondary);
}

.step-keywords {
  display: grid;
  gap: 6px;
  margin-top: 8px;
}

.keyword-group {
  display: grid;
  grid-template-columns: 22px 1fr;
  gap: 8px;
  align-items: start;
  font-size: var(--text-xs);
  line-height: var(--leading-normal);
}

.keyword-group span {
  width: 22px;
  height: 22px;
  border-radius: var(--radius-sm);
  background: var(--color-primary-light);
  color: var(--color-primary-dark);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
}

.keyword-group em {
  min-width: 0;
  color: var(--color-text-secondary);
  font-style: normal;
  word-break: break-word;
}

.step-metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.step-metrics span {
  padding: 3px 8px;
  border-radius: var(--radius-full);
  background: var(--color-bg-alt);
  border: 1px solid var(--color-border-light);
  color: var(--color-primary-dark);
  font-size: var(--text-xs);
  font-weight: 700;
}

.step-logs {
  display: grid;
  gap: 8px;
  margin-top: 9px;
}

.trace-card {
  border: 1px solid var(--color-border-light);
  border-radius: 8px;
  background: var(--color-bg-alt);
  overflow: hidden;
}

.trace-card summary {
  min-height: 34px;
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 10px;
  align-items: center;
  padding: 8px 10px;
  cursor: pointer;
  list-style: none;
}

.trace-card summary::-webkit-details-marker {
  display: none;
}

.trace-card summary span {
  min-width: 0;
  font-size: var(--text-xs);
  font-weight: 700;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trace-card summary em {
  font-style: normal;
  font-size: 11px;
  color: var(--color-primary-dark);
  background: var(--color-primary-light);
  border-radius: var(--radius-full);
  padding: 2px 7px;
  white-space: nowrap;
}

.trace-content {
  padding: 0 10px 10px;
  display: grid;
  gap: 8px;
}

.trace-content p {
  margin: 0;
  color: var(--color-text-secondary);
}

.trace-meta {
  display: grid;
  grid-template-columns: 74px 1fr;
  gap: 5px 8px;
  margin: 0;
  padding: 8px;
  border-radius: 8px;
  background: var(--color-surface);
}

.trace-meta dt,
.trace-meta dd {
  margin: 0;
  font-size: var(--text-xs);
  line-height: var(--leading-normal);
}

.trace-meta dt {
  color: var(--color-text-muted);
}

.trace-meta dd {
  min-width: 0;
  color: var(--color-text-secondary);
  word-break: break-word;
}

.trace-source-block {
  display: grid;
  gap: 6px;
  padding: 8px;
  border-radius: 8px;
  background: var(--color-surface);
  border: 1px solid var(--color-border-light);
}

.trace-source-block.verified {
  border-color: rgba(16, 185, 129, 0.28);
  background: rgba(209, 250, 229, 0.34);
}

.trace-source-block strong {
  font-size: var(--text-xs);
  color: var(--color-text);
}

.trace-source-block ol {
  display: grid;
  gap: 6px;
  margin: 0;
  padding-left: 18px;
}

.trace-source-block li {
  min-width: 0;
  font-size: var(--text-xs);
  line-height: var(--leading-normal);
  color: var(--color-text-secondary);
}

.trace-source-block li span,
.trace-source-block li small {
  display: block;
  min-width: 0;
  word-break: break-word;
}

.trace-source-block li span {
  color: var(--color-text);
  font-weight: 600;
}

.trace-source-block li small {
  margin-top: 2px;
  color: var(--color-text-muted);
}

.source-status {
  display: inline-flex;
  align-items: center;
  margin-left: 6px;
  padding: 1px 6px;
  border-radius: var(--radius-full);
  font-style: normal;
  font-size: 11px;
  font-weight: 700;
  color: var(--color-text-muted);
  background: var(--color-border-light);
}

.source-status.kept,
.source-status.retained {
  color: #047857;
  background: rgba(209, 250, 229, 0.84);
}

.source-status.filtered {
  color: #b91c1c;
  background: rgba(254, 226, 226, 0.9);
}

.source-status.candidate {
  color: #92400e;
  background: rgba(254, 243, 199, 0.88);
}

.trace-source-block li .reject-reason {
  color: #b91c1c;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* === Transition === */
.fade-enter-active,
.fade-leave-active {
  transition: opacity var(--transition-slow);
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* === 响应式 === */
@media (max-width: 768px) {
  .float-sidebar {
    left: 0;
    right: 0;
    top: auto;
    bottom: 0;
    width: 100%;
    height: 55vh;
    border-radius: var(--radius-xl) var(--radius-xl) 0 0;
    border-bottom: none;
  }

  .float-sidebar.collapsed {
    height: 48px;
    width: 48px;
    left: var(--space-lg);
    right: auto;
    bottom: var(--space-lg);
    border-radius: var(--radius-full);
  }

  .sidebar-toggle {
    top: -14px;
    right: auto;
    left: 50%;
    transform: translateX(-50%);
  }

  .float-sidebar.collapsed .sidebar-toggle {
    top: 10px;
    left: auto;
    right: 10px;
    transform: none;
  }

  .float-quickbar {
    top: 116px;
    left: var(--space-sm);
    right: var(--space-sm);
    transform: none;
    justify-content: center;
    overflow-x: auto;
  }

  .navbar-inner {
    padding: 0 var(--space-md);
  }

  .planning-card {
    padding: 18px;
    width: 100%;
  }

  .trace-meta {
    grid-template-columns: 1fr;
  }
}

@media (min-width: 1200px) {
  .float-sidebar {
    width: 440px;
  }

  .sidebar-workbench {
    padding: 12px;
    gap: 12px;
  }
}
</style>
