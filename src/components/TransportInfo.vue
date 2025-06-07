<template>
  <div class="transport-info">
    <h2>实时交通信息</h2>
    
    <div class="time-selector">
      <div class="time-input">
        <label for="departure-time">出家门赶车时间：</label>
        <input 
          type="datetime-local" 
          id="departure-time" 
          v-model="selectedDateTime"
          :min="minDateTime"
          :max="maxDateTime"
          @change="handleTimeChange"
        >
      </div>
      <div class="buttons-container">
        <button class="action-button reset-button" @click="resetToCurrentTime">
          <span class="button-icon">↻</span>
          重置为当前时间
        </button>
        <button class="action-button catch-button" @click="startCatching">
          <span class="button-icon">▶</span>
          开始赶车
        </button>
      </div>
    </div>

    <div class="transport-section">
      <h3>轻轨信息</h3>
      <div class="route-info">
        <p>从: Haymarket</p>
        <p>到: Juniors Kingsford</p>
      </div>
      <div class="schedule" v-if="lightRailSchedule.length">
        <div 
          v-for="(trip, index) in lightRailSchedule" 
          :key="index" 
          class="schedule-item" 
          :class="{
            'expired': isExpired(trip.departureTime),
            'target-trip': trip === targetLightRail
          }"
        >
          <div class="trip-info">
            <span class="route-number">{{ trip.routeNumber }}</span>
            <span class="duration">行程时间: {{ trip.duration }}分钟</span>
          </div>
          <div class="time-info">
            <span>发车: {{ formatTime(trip.departureTime) }}</span>
            <span>到达: {{ formatTime(trip.arrivalTime) }}</span>
          </div>
        </div>
      </div>
      <div v-else>{{ lightRailError || '加载中...' }}</div>
    </div>

    <div class="transport-section">
      <h3>公交信息 - 396路</h3>
      <div class="route-info">
        <p>从: Juniors Kingsford</p>
        <p>到: Guardian Childcare & Education Maroubra East</p>
      </div>
      <div class="schedule" v-if="busSchedule.length">
        <div 
          v-for="(trip, index) in busSchedule" 
          :key="index" 
          class="schedule-item" 
          :class="{
            'expired': isExpired(trip.departureTime),
            'target-trip': isTargetBus(trip)
          }"
        >
          <div class="trip-info">
            <span class="route-number">{{ trip.routeNumber }}</span>
            <div class="trip-details">
              <div class="platform-info" v-if="trip.platform">
                <span class="platform">站台: {{ trip.platform }}</span>
                <span v-if="isTargetBus(trip) && trip.arrivalTime" class="arrival-time">
                  预计到达: {{ formatTime(trip.arrivalTime) }}
                </span>
              </div>
              <span class="duration" v-if="trip.duration">
                行程时间: {{ trip.duration }}分钟
              </span>
            </div>
          </div>
          <div class="time-info">
            <span>发车: {{ formatTime(trip.departureTime) }}</span>
          </div>
        </div>
      </div>
      <div v-else>{{ busError || '加载中...' }}</div>
    </div>
  </div>
</template>
<script setup>
import { ref, onMounted, computed, nextTick } from 'vue'
import axios from 'axios'
import dayjs from 'dayjs'
import utc from 'dayjs/plugin/utc'
import timezone from 'dayjs/plugin/timezone'
import isBetween from 'dayjs/plugin/isBetween'
import { api } from '@/utils/api'

/* ─── day-js 配置 ───────────────────────────────────────── */
dayjs.extend(utc)
dayjs.extend(timezone)
dayjs.extend(isBetween)
dayjs.tz.setDefault('Australia/Sydney')

/* ─── 响应式数据 ─────────────────────────────────────────── */
const lightRailSchedule = ref([])
const busSchedule       = ref([])
const lightRailError    = ref('')
const busError          = ref('')
const selectedDateTime  = ref('')
const targetLightRail   = ref(null)  // 目标轻轨班次
const targetBusId       = ref(null)   // 目标公交ID

/* ─── 常量（站点 TSN）────────────────────────────────────── */
const LIGHT_RAIL_ORIGIN_ID = '10101606'
const LIGHT_RAIL_DEST_ID   = '203294'
const BUS_ORIGIN_ID        = '203290'     // Juniors Kingsford 平台 A（origin for 公交 396）
const BUS_DEST_ID          = '2035176'    // Garden St at Maroubra Rd
const BUS_STOP_ID          = '203290'     // 正确的公交站点ID

/* ─── 选时间控件范围（±7 天）────────────────────────────── */
const minDateTime = computed(() =>
  dayjs().tz().subtract(7, 'day').format('YYYY-MM-DDTHH:mm'))
const maxDateTime = computed(() =>
  dayjs().tz().add(7, 'day').format('YYYY-MM-DDTHH:mm'))

/* ─── 辅助函数 ───────────────────────────────────────────── */
function initializeDateTime () {
  // 初始化为当前时间后
  const now = dayjs().tz('Australia/Sydney')
  selectedDateTime.value = now.format('YYYY-MM-DDTHH:mm')
}

function resetToCurrentTime () { 
  initializeDateTime()
  // 重置时不自动开始赶车
  fetchSchedules()
}

function handleTimeChange() {
  // 时间改变时只刷新数据，不开始赶车
  fetchSchedules()
}

function formatTime (ts) { return dayjs.utc(ts).tz('Australia/Sydney').format('HH:mm') }
function isExpired (ts)  {
  return dayjs.utc(ts).tz('Australia/Sydney')
          .isBefore(dayjs.tz(selectedDateTime.value))
}

/* ─── 总调度 ─────────────────────────────────────────────── */
async function fetchSchedules(){
  await Promise.all([
    fetchLightRailSchedule(),
    fetchBusSchedule()
  ])
}

function isTargetBus(trip) {
  if (!trip?.departureTime) return false
  return trip.departureTime === targetBusId.value
}

async function fetchTargetBusTrip(bus) {
  if (!bus?.departureTime) return null
  
  try {
    const depTime = dayjs.utc(bus.departureTime).tz('Australia/Sydney')
    console.log('获取公交完整行程:', {
      发车时间: depTime.format('YYYY-MM-DD HH:mm:ss'),
      路线: bus.routeNumber
    })

    const res = await api.get('/v1/tp/trip', {
    
      params:{
        outputFormat:'rapidJSON',
        coordOutputFormat:'EPSG:4326',
        depArrMacro:'dep',
        itdDate: depTime.format('YYYYMMDD'),
        itdTime: depTime.format('HHmm'),
        type_origin: 'stop',
        name_origin: BUS_STOP_ID,
        type_destination: 'stop',
        name_destination: BUS_DEST_ID,
        calcNumberOfTrips: 3,          // 只要几个最近的班次
        TfNSWTR: true,
        wheelchair: false,
        lineID: bus.routeNumber        // 指定路线
      }
    })

    // 找到匹配的行程
    const matchingJourney = res.data.journeys?.find(journey => {
      const firstLeg = journey.legs[0]
      const plannedDep = firstLeg.origin.departureTimePlanned
      const estimatedDep = firstLeg.origin.departureTimeEstimated
      const journeyDep = estimatedDep || plannedDep
      
      // 确保是同一班车（发车时间匹配）
      return Math.abs(
        dayjs.utc(journeyDep).diff(dayjs.utc(bus.departureTime), 'minute')
      ) < 2  // 允许2分钟误差
    })

    if (matchingJourney) {
      const lastLeg = matchingJourney.legs[matchingJourney.legs.length - 1]
      const updatedBus = {
        ...bus,
        arrivalTime: lastLeg.destination.arrivalTimeEstimated || 
                    lastLeg.destination.arrivalTimePlanned,
        duration: Math.ceil(
          matchingJourney.legs.reduce((sum, leg) => sum + (leg.duration || 0), 0) / 60
        )
      }
      
      // 更新公交车列表中的信息
      const index = busSchedule.value.findIndex(b => b.departureTime === bus.departureTime)
      if (index !== -1) {
        busSchedule.value[index] = updatedBus
      }
      
      return updatedBus
    }
    
    return null
  } catch (e) {
    console.error('获取公交完整行程失败:', e)
    return null
  }
}

function startCatching () {
  if (!selectedDateTime.value) {
    alert('请先选择开始赶车时间')
    return
  }

  // 使用选择的时间加10分钟作为赶车时间
  const baseTime = dayjs(selectedDateTime.value).tz('Australia/Sydney')
  const catchTime = baseTime.add(10, 'minute')
  
  // 如果选择的时间已经过期，提示用户
  if (baseTime.isBefore(dayjs().tz('Australia/Sydney'))) {
    alert('请选择未来的时间')
    return
  }

  console.log('开始赶车计划:', {
    选择时间: baseTime.format('YYYY-MM-DD HH:mm:ss'),
    实际赶车时间: catchTime.format('YYYY-MM-DD HH:mm:ss')
  })

  // 清除之前的目标
  targetLightRail.value = null
  targetBusId.value = null
  
  // 重新获取数据
  fetchSchedules().then(async () => {
    // 找到赶车时间后的第一班轻轨
    targetLightRail.value = lightRailSchedule.value.find(trip => {
      const depTime = dayjs.utc(trip.departureTime).tz('Australia/Sydney')
      return depTime.isAfter(catchTime)
    })

    if (targetLightRail.value) {
      // 轻轨到达时间+1分钟作为公交起始时间
      const lightRailArrival = dayjs.utc(targetLightRail.value.arrivalTime)
                                   .tz('Australia/Sydney')
                                   .add(1, 'minute')
      
      // 找到最近的公交车
      const targetBus = busSchedule.value.find(trip => {
        const depTime = dayjs.utc(trip.departureTime).tz('Australia/Sydney')
        return depTime.isAfter(lightRailArrival)
      })

      // 设置目标公交ID
      if (targetBus) {
        targetBusId.value = targetBus.departureTime
        // 获取目标公交的完整行程信息
        await fetchTargetBusTrip(targetBus)
      }

      // 如果找到了目标班次，滚动到对应位置
      nextTick(() => {
        const lightRailEl = document.querySelector('.schedule-item.target-trip')
        if (lightRailEl) {
          lightRailEl.scrollIntoView({ behavior: 'smooth', block: 'center' })
        }
      })
    } else {
      alert('在选择的时间后没有找到合适的轻轨班次')
    }
  }).catch(error => {
    console.error('赶车计划获取失败:', error)
  })
}

/* ─── 获取轻轨 (+1h) ─────────────────────────────────────── */
async function fetchLightRailSchedule () {
  lightRailError.value = ''
  try {
    const base = dayjs.tz(selectedDateTime.value)             // 选中时间
    const res  = await api.get('/v1/tp/trip', {

      params :{
        outputFormat:'rapidJSON', coordOutputFormat:'EPSG:4326',
        depArrMacro:'dep',
        itdDate: base.format('YYYYMMDD'),
        itdTime: base.format('HHmm'),
        type_origin:'stop', name_origin: LIGHT_RAIL_ORIGIN_ID,
        type_destination:'stop', name_destination: LIGHT_RAIL_DEST_ID,
        TfNSWTR:'true', timeWindow:60, calcNumberOfTrips:30
      }
    })

    const windowStart = base
    const windowEnd   = base.clone().add(1,'hour')

    lightRailSchedule.value = (res.data.journeys || [])
      .map(j => {
        const leg = j.legs.find(l => l.transportation?.product?.name !== 'footpath')
        if (!leg) return null
        const total = j.legs.reduce((s,l)=> s+(l.duration||0), 0)
        return {
          departureTime: j.legs[0].origin.departureTimeEstimated
                      || j.legs[0].origin.departureTimePlanned,
          arrivalTime  : j.legs.slice(-1)[0].destination.arrivalTimeEstimated
                      || j.legs.slice(-1)[0].destination.arrivalTimePlanned,
          routeNumber  : leg.transportation?.number || '',
          duration     : Math.ceil(total/60)
        }
      })
      .filter(t=>{
        if(!t) return false
        const dep = dayjs.utc(t.departureTime).tz('Australia/Sydney')
        return dep.isBetween(windowStart, windowEnd, null, '[)')
      })

    if(!lightRailSchedule.value.length)
      lightRailError.value = '未来 1 小时内无轻轨班次'
  } catch (e) {
    console.error(e)
    lightRailError.value = '轻轨数据获取失败'
  }
}

/* ─── 获取公交 396 (+2h) ─────────────────────────────────────── */
async function fetchBusSchedule () {
  busError.value = ''
  try {
    const base = dayjs.tz(selectedDateTime.value)
    console.log('发起公交请求:', {
      时间: base.format('YYYY-MM-DD HH:mm:ss'),
      站点: BUS_STOP_ID
    })

    const res = await api.get('/v1/tp/departure_mon', {
      params :{
        outputFormat:'rapidJSON',
        coordOutputFormat:'EPSG:4326',

        // ============== 关键字段 ==============
        mode:'direct',            // 千万别写成 'departure'
        depArrMacro:'dep',        // 查出发；要查到站用 'arr'
        type_dm:'stop',
        name_dm: BUS_STOP_ID,     // 203290
        departureMonitorMacro:true,
        TfNSWDM:true,
        maxJourneys:80,           // 增加获取班次数量以适应2小时窗口

        // ---------- 线路过滤（可选）-----------
        lineFilterMode:'include', // include / exclude
        lineFilterList:'396',     // 多条可用分号；例： '396;M10'

        // ---------- 日期时间（可选）-----------
        itdDate: base.format('YYYYMMDD'), // 可以不传，后端会用"现在"
        itdTime: base.format('HHmm'),
        version:'10'              // 可以省
      }
    })

    console.log('公交API响应:', {
      请求参数: {
        日期: base.format('YYYYMMDD'),
        时间: base.format('HHmm'),
        站点: BUS_STOP_ID
      },
      响应数据: res.data
    })

    const windowStart = dayjs().tz('Australia/Sydney')  // 使用当前时间作为开始
    const windowEnd   = windowStart.clone().add(2,'hour')  // 改为2小时

    if (!res.data.stopEvents) {
      console.warn('未找到发车信息:', {
        时间窗口: {
          开始: windowStart.format('YYYY-MM-DD HH:mm:ss'),
          结束: windowEnd.format('YYYY-MM-DD HH:mm:ss')
        },
        响应数据: res.data
      })
      busError.value = '暂时无法获取发车信息'
      return
    }

    busSchedule.value = (res.data.stopEvents || [])
      .filter(e => {
        // 确保是 396 路公交
        const routeNum = e.transportation?.number?.trim()
        const isCorrectRoute = routeNum?.startsWith('396')
        
        // 确保在时间窗口内
        const depTime = e.departureTimeEstimated || e.departureTimePlanned
        if (!depTime) return false
        const depSYD = dayjs.utc(depTime).tz('Australia/Sydney')
        const isInWindow = depSYD.isBetween(windowStart, windowEnd, null, '[)')
        
        console.log('过滤班次:', {
          路线: routeNum,
          是否396: isCorrectRoute,
          发车时间: depSYD.format('YYYY-MM-DD HH:mm:ss'),
          在时间窗口内: isInWindow
        })
        
        return isCorrectRoute && isInWindow
      })
      .map(e => ({
        routeNumber: e.transportation.number,
        departureTime: e.departureTimeEstimated || e.departureTimePlanned,
        arrivalTime: null,
        duration: null,
        platform: e.location?.properties?.platform || '',
        realtime: !!e.departureTimeEstimated
      }))
      .sort((a,b) => 
        dayjs.utc(a.departureTime).tz('Australia/Sydney') - 
        dayjs.utc(b.departureTime).tz('Australia/Sydney')
      )

    if(!busSchedule.value.length)
      busError.value = '未来 2 小时内暂无 396 路班次'  // 更新错误信息
  } catch (e) {
    console.error('公交数据获取失败:', e)
    busError.value = '公交数据获取失败'
    if (e.response) {
      console.error('错误详情:', {
        状态: e.response.status,
        数据: e.response.data
      })
    }
  }
}

/* ─── 生命周期 ───────────────────────────────────────────── */
onMounted(()=>{
  initializeDateTime()
  fetchSchedules().catch(error => {
    console.error('初始数据获取失败:', error)
  })
  setInterval(()=>{ if(!selectedDateTime.value) resetToCurrentTime() }, 60000)
})
</script>



<style scoped>
.transport-info {
  max-width: 800px;
  margin: 0 auto;
  padding: 20px;
}

.time-selector {
  background: #fff;
  padding: 20px;
  border-radius: 8px;
  margin-bottom: 20px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.time-input {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.time-input label {
  font-weight: bold;
  color: #333;
  font-size: 1.1em;
}

.time-input input {
  padding: 12px;
  border: 1px solid #ddd;
  border-radius: 6px;
  font-size: 16px;
  width: 100%;
  transition: border-color 0.3s;
}

.time-input input:focus {
  border-color: #2196F3;
  outline: none;
  box-shadow: 0 0 0 2px rgba(33, 150, 243, 0.1);
}

.buttons-container {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
}

.action-button {
  padding: 12px;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 16px;
  font-weight: 500;
  transition: all 0.3s;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
}

.button-icon {
  font-size: 1.2em;
}

.reset-button {
  background-color: #f5f5f5;
  color: #666;
}

.reset-button:hover {
  background-color: #e0e0e0;
}

.catch-button {
  background-color: #2196F3;
  color: white;
}

.catch-button:hover {
  background-color: #1976D2;
}

.transport-section {
  background: #f5f5f5;
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 20px;
}

.route-info {
  margin: 10px 0;
  padding: 10px;
  background: #fff;
  border-radius: 4px;
}

.schedule {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.schedule-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  background: #fff;
  padding: 12px;
  border-radius: 4px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
}

.trip-info {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.trip-details {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-left: 8px;
}

.platform-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.arrival-time {
  color: #2196F3;
  font-size: 0.9em;
  font-weight: 500;
}

.schedule-item.target-trip .arrival-time {
  color: #1B5E20;  /* 深绿色 */
  font-weight: bold;
}

.time-info {
  display: flex;
  justify-content: space-between;
  font-weight: bold;
}

.route-number {
  font-weight: bold;
  color: #4CAF50;
}

.duration {
  color: #666;
  font-size: 0.9em;
}

h2 {
  color: #333;
  margin-bottom: 20px;
}

h3 {
  color: #666;
  margin-bottom: 15px;
}

@media (min-width: 768px) {
  .time-selector {
    max-width: 500px;
    margin-left: auto;
    margin-right: auto;
  }

  .buttons-container {
    flex-direction: row;
  }

  .action-button {
    flex: 1;
  }
}

.schedule-item.expired {
  background-color: rgba(255,0,0,0.1) !important;
}

.schedule-item.target-trip {
  background-color: #C8E6C9 !important;  /* 浅绿色背景 */
  border-left: 4px solid #4CAF50;        /* 绿色左边框 */
  animation: highlight 1s ease-in-out;    /* 添加动画效果 */
}

@keyframes highlight {
  0% { background-color: #FFFFFF; }
  50% { background-color: #81C784; }
  100% { background-color: #C8E6C9; }
}
</style> 