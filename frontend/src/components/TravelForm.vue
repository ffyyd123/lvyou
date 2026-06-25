<template>
  <div class="travel-form">
    <div class="form-header">
      <div>
        <h2>规划新旅程</h2>
        <p>输入目的地、天数和偏好，生成可执行路线。</p>
      </div>
      <IconSparkles size="22" color="var(--color-primary)" :stroke-width="1.8" />
    </div>

    <form @submit.prevent="handleSubmit" class="form-body">
      <section class="route-card">
        <div class="field">
          <label for="from-city">出发</label>
          <div class="select-shell">
            <IconMapPin size="16" />
            <select
              id="from-city"
              v-model="form.from"
              required
              :disabled="districtLoading"
            >
              <option value="" disabled>选择省份或城市</option>
              <option
                v-for="region in flatDistricts"
                :key="`from-${region.key}`"
                :value="region.name"
              >
                {{ region.label }}
              </option>
            </select>
          </div>
        </div>

        <button type="button" class="swap-btn" title="交换出发地和目的地" @click="swapCities">
          <IconArrowRight size="16" />
        </button>

        <div class="field">
          <label for="to-city">目的地</label>
          <div class="select-shell">
            <IconMapPin size="16" />
            <select
              id="to-city"
              v-model="form.to"
              required
              :disabled="districtLoading"
            >
              <option value="" disabled>选择省份或城市</option>
              <option
                v-for="region in flatDistricts"
                :key="`to-${region.key}`"
                :value="region.name"
              >
                {{ region.label }}
              </option>
            </select>
          </div>
        </div>
      </section>

      <div class="district-status" v-if="districtLoading || districtError">
        <span v-if="districtLoading" class="status-loading">正在加载国内行政区...</span>
        <button v-else type="button" class="status-retry" @click="loadDistricts">
          行政区加载失败，重试
        </button>
      </div>

      <section class="compact-row">
        <div class="field days-field">
          <label>天数</label>
          <div class="days-input">
            <button type="button" class="step-btn" @click="decrementDays" :disabled="form.days <= 1">
              <IconMinus size="15" />
            </button>
            <input
              v-model.number="form.days"
              type="number"
              min="1"
              max="30"
              required
              class="days-value"
            />
            <button type="button" class="step-btn" @click="incrementDays" :disabled="form.days >= 30">
              <IconPlus size="15" />
            </button>
            <span class="days-unit">天</span>
          </div>
        </div>

        <div class="field quick-field">
          <label>快速选择</label>
          <div class="quick-days">
            <button
              v-for="day in quickDays"
              :key="day"
              type="button"
              :class="['quick-day', { active: form.days === day }]"
              @click="form.days = day"
            >
              {{ day }}天
            </button>
          </div>
        </div>
      </section>

      <section class="field">
        <label>旅行偏好</label>
        <div class="preference-grid">
          <label
            v-for="pref in preferences"
            :key="pref.value"
            :class="['pref-card', { active: form.preference === pref.value }]"
          >
            <input type="radio" v-model="form.preference" :value="pref.value" hidden />
            <component :is="pref.icon" size="18" />
            <span>{{ pref.label }}</span>
          </label>
        </div>
      </section>

      <section class="field">
        <div class="label-row">
          <label for="user-idea">补充要求</label>
          <span>{{ form.userIdea.length }}/500</span>
        </div>
        <textarea
          id="user-idea"
          v-model="form.userIdea"
          placeholder="例如：优先古城和寺庙，每天不要太累，避开爬山路线。"
          class="user-idea-input"
          rows="3"
          maxlength="500"
        ></textarea>
      </section>

      <section class="online-research-row">
        <label class="switch-control">
          <input type="checkbox" v-model="form.onlineResearch" />
          <span class="switch-track"></span>
          <span class="switch-text">
            <strong>联网实时规划</strong>
            <small>整合小红书、抖音、高德、攻略站等公开搜索信号</small>
          </span>
        </label>
      </section>

      <div class="form-actions">
        <button type="button" class="reset-btn" :disabled="loading || submitting" @click="resetForm">
          重置
        </button>
        <button type="submit" class="submit-btn" :disabled="loading || submitting">
          <template v-if="!loading && !submitting">
            <IconSend size="17" />
            开始规划
          </template>
          <template v-else>
            <span class="btn-spinner"></span>
            AI 规划中
          </template>
        </button>
      </div>
    </form>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import axios from 'axios'
import { toast } from '../utils/toast.js'
import provinceList from 'province-city-china/dist/province.json'
import cityList from 'province-city-china/dist/city.json'
import {
  IconArrowRight,
  IconLandmark,
  IconMapPin,
  IconMinus,
  IconMountain,
  IconPlus,
  IconSend,
  IconSparkles,
  IconTarget,
  IconUtensils
} from '../icons/index.js'

const emit = defineEmits(['planGenerated', 'researchPreview'])
const props = defineProps({
  loading: Boolean
})

const submitting = ref(false)
const districtLoading = ref(false)
const districtError = ref('')
const districts = ref(buildDistricts())

const defaultForm = {
  from: '北京',
  to: '山西',
  days: 5,
  preference: '历史文化',
  userIdea: '',
  onlineResearch: true
}

const form = reactive({ ...defaultForm })
const quickDays = [2, 3, 5, 7]

const preferences = [
  { value: '历史文化', label: '历史文化', icon: IconLandmark },
  { value: '自然风光', label: '自然风光', icon: IconMountain },
  { value: '美食', label: '美食之旅', icon: IconUtensils },
  { value: '综合', label: '综合体验', icon: IconTarget }
]

const flatDistricts = computed(() => {
  return districts.value.flatMap((province) => {
    const provinceOption = {
      key: province.adcode || province.name,
      name: normalizeDistrictName(province.name),
      label: `省份｜${province.name}`
    }
    const cityOptions = (province.children || []).map(city => ({
      key: city.adcode || `${province.name}-${city.name}`,
      name: normalizeDistrictName(city.name),
      label: `　城市｜${city.name}`
    }))
    return [provinceOption, ...cityOptions]
  })
})

onMounted(() => {
  loadDistricts()
})

function loadDistricts() {
  districtError.value = ''
  districtLoading.value = false
  districts.value = buildDistricts()
}

function buildDistricts() {
  return provinceList.map(province => ({
    name: province.name,
    adcode: province.code,
    level: 'province',
    children: cityList
      .filter(city => city.province === province.province)
      .map(city => ({
        name: city.name,
        adcode: city.code,
        level: 'city'
      }))
  }))
}

function normalizeDistrictName(name) {
  return (name || '')
    .replace(/特别行政区$/, '')
    .replace(/维吾尔自治区$/, '')
    .replace(/壮族自治区$/, '')
    .replace(/回族自治区$/, '')
    .replace(/自治区$/, '')
    .replace(/省$/, '')
    .replace(/市$/, '')
}

function swapCities() {
  const from = form.from
  form.from = form.to
  form.to = from
}

function resetForm() {
  Object.assign(form, defaultForm)
}

function incrementDays() {
  if (form.days < 30) form.days++
}

function decrementDays() {
  if (form.days > 1) form.days--
}

async function handleSubmit() {
  if (props.loading || submitting.value) return

  submitting.value = true
  const payload = {
    from: form.from,
    to: form.to,
    days: form.days,
    preference: form.preference,
    userIdea: form.userIdea || undefined,
    onlineResearch: form.onlineResearch
  }

  try {
    emit('planGenerated', null)

    if (form.onlineResearch) {
      try {
        const keywordResponse = await axios.post('/api/research/keywords', payload, {
          timeout: 45000
        })
        if (keywordResponse.data?.code === 200) {
          emit('researchPreview', keywordResponse.data.data)
        }
      } catch (keywordError) {
        console.warn('搜索关键词预览失败:', keywordError)
      }

      try {
        const previewResponse = await axios.post('/api/research/preview', payload, {
          timeout: 120000
        })
        if (previewResponse.data?.code === 200) {
          emit('researchPreview', previewResponse.data.data)
        }
      } catch (previewError) {
        console.warn('联网调研预览失败:', previewError)
        emit('researchPreview', {
          enabled: true,
          status: 'preview_failed',
          destination: form.to,
          rawSourceCount: 0,
          cleanedSourceCount: 0,
          searchRounds: [],
          keywordGroups: {},
          targetKeywordCount: 15,
          targetSourcesPerKeyword: 20,
          targetEffectiveSourceCount: 300
        })
      }
    }

    const response = await axios.post('/api/travel/plan', payload, {
      timeout: 240000
    })

    if (response.data && response.data.code === 200) {
      emit('planGenerated', response.data.data)
    } else {
      emit('planGenerated', {})
      toast(response.data?.message || '路线规划失败', 'error')
    }
  } catch (error) {
    console.error('请求失败:', error)
    emit('planGenerated', {})
    if (error.code === 'ECONNABORTED') {
      toast('请求超时，AI 推理时间过长，请减少天数或重试', 'warning')
    } else if (error.response?.status === 500) {
      toast('服务器内部错误，请稍后重试', 'error')
    } else {
      toast(error.response?.data?.message || error.message || '网络请求失败', 'error')
    }
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.travel-form {
  background: var(--color-surface);
}

.form-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-md);
  padding: 18px 20px 14px;
  border-bottom: 1px solid var(--color-border-light);
}

.form-header h2 {
  font-size: var(--text-xl);
  line-height: var(--leading-tight);
  font-weight: 700;
  color: var(--color-text);
}

.form-header p {
  margin-top: 4px;
  font-size: var(--text-sm);
  color: var(--color-text-muted);
}

.form-body {
  padding: 16px 20px 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.route-card {
  display: grid;
  grid-template-columns: 1fr 32px 1fr;
  align-items: end;
  gap: 8px;
}

.compact-row {
  display: grid;
  grid-template-columns: 1fr 1.1fr;
  gap: 12px;
  align-items: end;
}

.field {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.field label,
.label-row label {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--color-text-secondary);
}

.label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
}

.label-row span {
  font-size: var(--text-xs);
  color: var(--color-text-muted);
}

.input-shell,
.select-shell {
  height: 40px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 11px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg-alt);
  color: var(--color-text-muted);
  transition: border-color var(--transition-fast), background var(--transition-fast), box-shadow var(--transition-fast);
}

.input-shell:focus-within,
.select-shell:focus-within {
  border-color: var(--color-primary);
  background: var(--color-surface);
  box-shadow: 0 0 0 3px rgba(14, 165, 233, 0.12);
}

.input-shell input,
.select-shell select {
  width: 100%;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  font: inherit;
  font-size: var(--text-md);
  color: var(--color-text);
}

.select-shell select {
  height: 38px;
  cursor: pointer;
  appearance: auto;
}

.select-shell select:disabled {
  cursor: wait;
  color: var(--color-text-muted);
}

.district-status {
  min-height: 28px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
}

.status-loading {
  font-size: var(--text-xs);
  color: var(--color-text-muted);
}

.status-retry {
  height: 28px;
  padding: 0 10px;
  border: 1px solid var(--color-warning);
  border-radius: 8px;
  background: var(--color-warning-light);
  color: #92400e;
  font-family: inherit;
  font-size: var(--text-xs);
  font-weight: 700;
  cursor: pointer;
}

.swap-btn {
  width: 32px;
  height: 32px;
  margin-bottom: 4px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-full);
  background: var(--color-surface);
  color: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.swap-btn:hover {
  background: var(--color-primary-light);
  border-color: var(--color-primary);
}

.days-input {
  height: 40px;
  display: flex;
  align-items: center;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg-alt);
  overflow: hidden;
}

.step-btn {
  width: 36px;
  height: 38px;
  border: none;
  background: transparent;
  color: var(--color-text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.step-btn:hover:not(:disabled) {
  background: var(--color-primary-light);
  color: var(--color-primary-dark);
}

.step-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.days-value {
  width: 44px;
  height: 38px;
  border: none;
  outline: none;
  background: transparent;
  text-align: center;
  font-size: var(--text-lg);
  font-weight: 700;
  color: var(--color-text);
  -moz-appearance: textfield;
}

.days-value::-webkit-outer-spin-button,
.days-value::-webkit-inner-spin-button {
  -webkit-appearance: none;
}

.days-unit {
  padding-right: 10px;
  font-size: var(--text-sm);
  color: var(--color-text-muted);
}

.quick-days {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 6px;
}

.quick-day {
  height: 40px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text-secondary);
  font-size: var(--text-sm);
  font-weight: 600;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.quick-day:hover,
.quick-day.active {
  border-color: var(--color-primary);
  background: var(--color-primary-light);
  color: var(--color-primary-dark);
}

.preference-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}

.pref-card {
  min-height: 58px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  cursor: pointer;
  color: var(--color-text-secondary);
  background: var(--color-bg-alt);
  transition: all var(--transition-fast);
}

.pref-card span {
  font-size: var(--text-xs);
  font-weight: 600;
  white-space: nowrap;
}

.pref-card:hover,
.pref-card.active {
  border-color: var(--color-primary);
  background: var(--color-primary-light);
  color: var(--color-primary-dark);
  box-shadow: 0 0 0 3px rgba(14, 165, 233, 0.08);
}

.user-idea-input {
  width: 100%;
  min-height: 78px;
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  font-size: var(--text-base);
  font-family: inherit;
  resize: vertical;
  outline: none;
  background: var(--color-bg-alt);
  transition: border-color var(--transition-fast), background var(--transition-fast), box-shadow var(--transition-fast);
  line-height: var(--leading-relaxed);
}

.user-idea-input:focus {
  border-color: var(--color-primary);
  background: var(--color-surface);
  box-shadow: 0 0 0 3px rgba(14, 165, 233, 0.12);
}

.user-idea-input::placeholder {
  color: var(--color-text-muted);
}

.online-research-row {
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: linear-gradient(135deg, var(--color-primary-light), var(--color-accent-light));
}

.switch-control {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 10px;
  align-items: center;
  cursor: pointer;
}

.switch-control input {
  position: absolute;
  opacity: 0;
  pointer-events: none;
}

.switch-track {
  width: 42px;
  height: 24px;
  border-radius: var(--radius-full);
  background: var(--color-border);
  position: relative;
  transition: background var(--transition-fast);
}

.switch-track::after {
  content: '';
  position: absolute;
  top: 3px;
  left: 3px;
  width: 18px;
  height: 18px;
  border-radius: var(--radius-full);
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
  transition: transform var(--transition-fast);
}

.switch-control input:checked + .switch-track {
  background: var(--color-primary);
}

.switch-control input:checked + .switch-track::after {
  transform: translateX(18px);
}

.switch-text {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.switch-text strong {
  font-size: var(--text-sm);
  color: var(--color-text);
}

.switch-text small {
  font-size: var(--text-xs);
  color: var(--color-text-secondary);
  line-height: var(--leading-normal);
}

.form-actions {
  display: grid;
  grid-template-columns: 92px 1fr;
  gap: 10px;
}

.reset-btn,
.submit-btn {
  height: 44px;
  border: none;
  border-radius: 8px;
  font-family: inherit;
  font-size: var(--text-base);
  font-weight: 700;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.reset-btn {
  background: var(--color-bg-alt);
  color: var(--color-text-secondary);
  border: 1px solid var(--color-border);
}

.reset-btn:hover:not(:disabled) {
  background: var(--color-surface-hover);
}

.submit-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: linear-gradient(135deg, var(--color-primary), var(--color-primary-dark));
  color: white;
  box-shadow: 0 8px 20px rgba(14, 165, 233, 0.22);
}

.submit-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 10px 24px rgba(14, 165, 233, 0.3);
}

.reset-btn:disabled,
.submit-btn:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.btn-spinner {
  width: 17px;
  height: 17px;
  border: 2px solid rgba(255,255,255,0.35);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

@media (max-width: 520px) {
  .route-card,
  .compact-row {
    grid-template-columns: 1fr;
  }

  .swap-btn {
    transform: rotate(90deg);
    margin: 0 auto;
  }

  .preference-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
