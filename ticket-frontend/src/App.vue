<script setup>
import { ref, computed, onMounted } from 'vue'
import * as api from './api.js'
import SlaChart from './components/SlaChart.vue'
import AiExplain from './components/AiExplain.vue'

const tab = ref('submit')
const toast = ref('')

function msg(s) { toast.value = s; setTimeout(() => (toast.value = ''), 3000) }

/* ---------- 提交工单 ---------- */
const form = ref({ title: '', description: '', priority: 2, slaHours: 24, category: '' })
const aiPreview = ref(null)
const lastCreated = ref(null)

async function previewAi() {
  if (!form.value.title) return msg('请先填写标题')
  const r = await api.aiRoute(form.value.title, form.value.description)
  aiPreview.value = r.data.data
}
async function submit() {
  if (!form.value.title) return msg('标题必填')
  const payload = { ...form.value }
  if (!payload.category) delete payload.category
  const r = await api.createTicket(payload)
  lastCreated.value = r.data.data
  form.value = { title: '', description: '', priority: 2, slaHours: 24, category: '' }
  aiPreview.value = null
  msg('工单已创建，流程已启动')
}

/* ---------- 工单列表 ---------- */
const tickets = ref([])
async function refreshTickets() {
  const r = await api.listTickets()
  tickets.value = r.data.data
}

/* ---------- 待办 ---------- */

// 预设角色：一键切换常用账号
const PRESETS = [
  { label: '初审(张工)',     user: 'zhang',      group: '',             filterNode: '初审' },
  { label: '复审A',         user: 'reviewerA',   group: '' },
  { label: '复审B',         user: 'reviewerB',   group: '' },
  { label: '财务打款(李财务)', user: 'li',        group: 'finance-group', filterNode: '财务打款' },
]
const currentUser = ref('zhang')
const group = ref('hardware-group')
const tasks = ref([])
const activePreset = ref('')
const queryMode = ref('')  // 'group' | 'mine' | 'all'
const litWindow = ref('')   // 操作后亮灯的窗口
const auditFilter = ref('') // 预设窗口过滤：只显示归属于当前激活窗口节点的任务
let litTimer = null

// 驳回理由：主待办「驳回」内联输入
const rejectingTask = ref('')     // 当前正在填写驳回理由的 taskId
const rejectReasonInput = ref('') // 驳回理由文本
const dynamicRejectReason = ref({}) // 列表页动态驳回理由，按工单 id 存

/** 判断一条任务归属哪个审批窗口（对应审批人） */
function taskWindow(t) {
  const a = t.assignee
  if (t.nodeName === '复审') {
    if (a === 'reviewerA') return '复审A'
    if (a === 'reviewerB') return '复审B'
    return '复审A'          // 顺序复审未认领时默认当前轮到 reviewerA
  }
  if (t.nodeName === '财务打款') return '财务打款(李财务)'
  if (t.nodeName === '初审' || t.nodeName === '补充材料') return '初审(张工)'
  return ''
}
/** 当前列表里哪些窗口有待办（持续亮灯用）—— 基于原始全量 tasks */
const taskWindows = computed(() => {
  const m = {}
  for (const t of tasks.value) {
    const w = taskWindow(t)
    if (w) m[w] = (m[w] || 0) + 1
  }
  return m
})
/** 前端过滤后的待办列表：有 filterNode 时只显示「归属于当前激活窗口节点」的任务 */
const filteredTasks = computed(() => {
  if (!auditFilter.value) return tasks.value
  return tasks.value.filter(t => taskWindow(t) === activePreset.value)
})
/** 点亮指定审批窗口（绿色脉冲），label 来自任务归属 */
function lightWindow(label) {
  if (!label) return
  litWindow.value = label
  clearTimeout(litTimer)
  litTimer = setTimeout(() => { litWindow.value = '' }, 2500)
}

let lastQueryFn = null // 记住上次用的查询方式，操作后自动刷新同一种

function applyPreset(p) {
  currentUser.value = p.user
  group.value = p.group !== undefined && p.group !== '' ? p.group : ''
  activePreset.value = p.label
  auditFilter.value = p.filterNode || ''
  if (auditFilter.value && !group.value) {
    // 初审模式：候选组是动态的(hardware/finance/permission)，只能查全部待办再按节点过滤
    queryMode.value = 'all'
    queryAll()
  } else if (group.value) {
    // 财务打款等：组固定(finance-group)，按组查待办 + 前端过滤节点
    queryMode.value = 'group'
    queryGroup()
  } else {
    // 复审A/B：无组，按人名查候选任务
    queryMode.value = 'mine'
    queryMine()
  }
}

async function queryGroup() {
  lastQueryFn = queryGroup; queryMode.value = 'group';
  // 同时查：①该候选组的未认领池（taskCandidateGroup）+ ②当前用户已认领的任务
  // 因为 Flowable 的 taskCandidateGroup 不返回已被 assignee 接走的任务，
  // 单纯按组查会漏掉自己正在处理的工单。
  const [gr, mr] = await Promise.all([
    api.listTasks(group.value, ''),
    api.listTasks('', currentUser.value),
  ])
  const all = [...(gr.data.data||[]), ...(mr.data.data||[])]
  // 按 taskId 去重（同一任务可能同时出现在两组结果中）
  const seen = new Set()
  tasks.value = all.filter(t => seen.has(t.taskId) ? false : (seen.add(t.taskId), true))
}
async function queryMine() { lastQueryFn = queryMine; queryMode.value = 'mine'; const r = await api.listTasks('', currentUser.value); tasks.value = r.data.data }
async function queryAll()  { lastQueryFn = queryAll;  queryMode.value = 'all';  const r = await api.listTasks('', '');              tasks.value = r.data.data }
/** 全部待办：清空窗口过滤，查看所有节点任务 */
function showAll() { auditFilter.value = ''; activePreset.value = ''; queryAll() }

/** 刷新：使用上次同样的查询方式 */
function refreshTasks() { if (lastQueryFn) lastQueryFn() }

/** 接单 */
async function doClaim(t) {
  await api.claim(t.ticketId, t.taskId, currentUser.value)
  msg('已接单')
  lightWindow(taskWindow(t))   // 点亮该任务对应的审批人窗口
  refreshTasks()
}
/** 审批/处理 */
async function doComplete(t, approved) {
  await api.completeTask(t.ticketId, t.taskId, currentUser.value, approved)
  msg(approved ? '已通过' : '已驳回')
  lightWindow(taskWindow(t))   // 点亮该任务对应的审批人窗口
  refreshTasks()
}

/* ---------- 驳回理由（人在回路合规） ---------- */
function startReject(t) {
  rejectingTask.value = t.taskId
  rejectReasonInput.value = ''
}
function cancelReject() {
  rejectingTask.value = ''
  rejectReasonInput.value = ''
}
async function confirmReject(t) {
  const reason = rejectReasonInput.value.trim()
  await api.completeTask(t.ticketId, t.taskId, currentUser.value, false, reason)
  msg(reason ? '已驳回：' + reason : '已驳回')
  rejectingTask.value = ''
  rejectReasonInput.value = ''
  lightWindow(taskWindow(t))
  refreshTasks()
}
/** 一键接单 + 通过（最常用操作，两步合一）*/
async function doClaimAndApprove(t) {
  try {
    await api.claim(t.ticketId, t.taskId, currentUser.value)
    await api.completeTask(t.ticketId, t.taskId, currentUser.value, true)
    msg('已接单并通过')
    lightWindow(taskWindow(t)) // 点亮该任务对应的审批人窗口
    refreshTasks()
  } catch(e) {
    msg('操作失败: ' + (e.response?.data?.message || e.message))
  }
}

/* ---------- 节点辅助信息 ---------- */
const NODE_HINTS = {
  '初审':       { step: 1, icon: '✍️', desc: '第1步：分类组审批，通过进复审，驳回回补充材料', color: '#4a90d9' },
  '复审':       { step: 2, icon: '🔍', desc: '第2步：顺序复审（reviewerA 先审，通过后 reviewerB 再审，任一驳回回补充材料；前一审批人未审完，后一审批人看不到此工单）', color: '#e67e22' },
  '补充材料':   { step: '-', icon: '📝', desc: '驳回回补：补完材料后重新提交到初审', color: '#95a5a6' },
  '财务打款':   { step: 3, icon: '💰', desc: '第3步：财务确认打款，通过后自动归档完成', color: '#27ae60' },
}
function nodeHint(name) { return NODE_HINTS[name] || { step: '?', icon: '📋', desc: name, color: '#888' } }

/* ---------- 列表操作 ---------- */
const rejectTarget = ref({})
async function doSuspend(id) { await api.suspend(id); refreshTickets(); msg('已挂起') }
async function doResume(id) { await api.resume(id); refreshTickets(); msg('已恢复') }
async function doReject(id) {
  const target = rejectTarget.value[id]
  if (!target) return msg('请选择驳回目标节点')
  const reason = (dynamicRejectReason.value[id] || '').trim()
  await api.rejectDynamic(id, target, reason); refreshTickets(); msg('已动态驳回至 ' + target + (reason ? '：' + reason : ''))
}
async function doDelete(id) {
  if (!confirm('确认删除该工单？将同时清除其流程与历史，且不可恢复。')) return
  await api.deleteTicket(id); refreshTickets(); msg('已删除工单 ' + id.slice(-6))
}

/* ---------- SLA 仪表盘 ---------- */
const slaStats = ref([])
async function refreshSla() {
  const r = await api.sla()
  slaStats.value = r.data.data
}

/* ---------- AI 人工复核队列（人在回路闭环） ---------- */
const reviewTickets = ref([])
const reviewEvidence = ref({})   // ticketId -> RouteResult（Top-K 证据）
const reviewOverride = ref({})   // ticketId -> 改判分类
async function refreshReview() {
  const r = await api.listTickets(null, null, true)
  reviewTickets.value = r.data.data || []
  // 逐条拉取 AI 证据，用于解释条展示“为什么这么分”
  reviewEvidence.value = {}
  for (const t of reviewTickets.value) {
    try {
      const ev = await api.aiRoute(t.title, t.description)
      reviewEvidence.value[t.id] = ev.data.data
    } catch (e) { /* 单条失败不影响整体 */ }
  }
}
async function doAiConfirm(t) {
  const override = reviewOverride.value[t.id]
  try {
    await api.aiConfirm(t.id, override || '')
    msg('已确认分派，流程已启动（人工结论已回流向量库）')
    refreshReview()
  } catch (e) {
    msg('确认失败: ' + (e.response?.data?.message || e.message))
  }
}

onMounted(() => { refreshTickets(); refreshSla() })

const catClass = (c) => ({
  '硬件类': 'tag-hardware', '财务类': 'tag-finance', '权限类': 'tag-permission'
}[c] || '')
const fmt = (v) => v == null ? '-' : v
</script>

<template>
  <div class="app">
    <header>
      <h1>企业级工单智能分流与协同处理系统</h1>
      <span class="muted">Spring Boot · Flowable · AI 向量分派 · Redis 锁 · ECharts</span>
    </header>

    <div class="tabs">
      <div :class="['tab', tab === 'submit' && 'active']" @click="tab = 'submit'">提交工单</div>
      <div :class="['tab', tab === 'list' && 'active']" @click="tab = 'list'; refreshTickets()">工单列表</div>
      <div :class="['tab', tab === 'tasks' && 'active']" @click="tab = 'tasks'">待办处理</div>
      <div :class="['tab', tab === 'review' && 'active']" @click="tab = 'review'; refreshReview()">AI 复核<span v-if="reviewTickets.length" class="tab-badge">{{ reviewTickets.length }}</span></div>
      <div :class="['tab', tab === 'sla' && 'active']" @click="tab = 'sla'; refreshSla()">SLA 仪表盘</div>
    </div>

    <div v-if="toast" :class="['hint', toast.includes('失败') ? 'error-hint' : '']">{{ toast }}</div>

    <!-- 提交 -->
    <section v-show="tab === 'submit'" class="panel">
      <label>标题 *</label>
      <input v-model="form.title" placeholder="例如：笔记本无法开机，疑似电池故障" />
      <label>描述</label>
      <textarea v-model="form.description" rows="3" placeholder="补充细节，AI 将据此语义分派"></textarea>
      <div class="row">
        <div class="col">
          <label>优先级</label>
          <select v-model.number="form.priority">
            <option :value="1">低</option><option :value="2">中</option><option :value="3">高</option>
          </select>
        </div>
        <div class="col">
          <label>SLA 时长(小时)</label>
          <input type="number" v-model.number="form.slaHours" />
        </div>
        <div class="col">
          <label>人工指定分类(可选)</label>
          <select v-model="form.category">
            <option value="">AI 自动分派</option>
            <option value="硬件类">硬件类</option>
            <option value="财务类">财务类</option>
            <option value="权限类">权限类</option>
          </select>
        </div>
      </div>
      <div style="margin-top:12px">
        <button class="ghost" @click="previewAi">AI 预判</button>
        <button @click="submit">提交工单</button>
      </div>

      <div v-if="aiPreview">
        <AiExplain :result="aiPreview" />
      </div>
      <div v-if="lastCreated" class="hint">
        已创建工单 <b>{{ lastCreated.id.slice(-6) }}</b>
        ｜ 分类：<span :class="['tag', catClass(lastCreated.category)]">{{ lastCreated.category }}</span>
        ｜ 状态：{{ lastCreated.status }}
        <span v-if="lastCreated.aiReviewRequired" class="warn-badge">⚠ 置信度低，已转入 AI 复核队列</span>
        <span v-else class="muted">（AI 自动分派，流程已启动）</span>
      </div>
    </section>

    <!-- 列表 -->
    <section v-show="tab === 'list'" class="panel">
      <button class="ghost" @click="refreshTickets">刷新</button>
      <table style="margin-top:8px">
        <thead><tr><th>ID</th><th>标题</th><th>分类</th><th>状态</th><th>当前节点</th><th>处理人</th><th>AI置信</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="t in tickets" :key="t.id">
            <td class="muted">{{ t.id.slice(-6) }}</td>
            <td>{{ t.title }}</td>
            <td><span :class="['tag', catClass(t.category)]">{{ t.category }}</span></td>
            <td>{{ t.status }}</td>
            <td>
              <span v-if="t.currentNode" :style="{color: nodeHint(t.currentNode).color, fontWeight:'bold'}">{{ t.currentNode }}</span>
              <span v-else class="muted">-</span>
            </td>
            <td>{{ fmt(t.assignee) }}</td>
            <td>{{ t.aiConfidence == null ? '-' : t.aiConfidence.toFixed(2) }}</td>
            <td>
              <button v-if="t.status === 'PROCESSING'" class="ghost" @click="doSuspend(t.id)">挂起</button>
              <button v-if="t.status === 'SUSPENDED'" class="ok" @click="doResume(t.id)">恢复</button>
              <select v-model="rejectTarget[t.id]" style="width:auto;display:inline-block">
                <option value="">动态驳回至…</option>
                <option value="audit1">初审</option>
                <option value="revise">补充材料</option>
                <option value="pay">财务打款</option>
              </select>
              <input v-model="dynamicRejectReason[t.id]" placeholder="驳回理由(选填)" style="width:130px;display:inline-block" />
              <button v-if="rejectTarget[t.id]" class="danger" @click="doReject(t.id)">执行</button>
              <button class="danger" @click="doDelete(t.id)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <!-- 待办处理 -->
    <section v-show="tab === 'tasks'" class="panel">

      <!-- 角色切换区 -->
      <div style="background:#1a1a2e;border-radius:8px;padding:12px;margin-bottom:14px">
        <div style="font-size:13px;color:#aaa;margin-bottom:6px">👨‍🎓 当前身份</div>
        <div class="row" style="align-items:center">
          <div class="col">
            <label>用户名</label><input v-model="currentUser" />
          </div>
          <div class="col">
            <label>候选人组(可选)</label><input v-model="group" placeholder="留空则按用户名查" />
          </div>
        </div>
        <div style="margin-top:8px;display:flex;gap:6px;flex-wrap:wrap">
          <button v-for="(p,i) in PRESETS" :key="i"
                  :class="['preset-btn', activePreset === p.label && 'active', taskWindows[p.label] && 'has-task', litWindow === p.label && 'lit']"
                  @click="applyPreset(p)">
            {{ p.label }}
            <span v-if="taskWindows[p.label]" class="win-count">{{ taskWindows[p.label] }}</span>
          </button>
        </div>
        <!-- 自动显示当前查询方式，不让用户猜 -->
        <div v-if="queryMode" class="muted" style="margin-top:6px;font-size:12px">
          查询方式：<b>{{ auditFilter ? '窗口过滤模式（仅显示「' + activePreset + '」窗口的任务）' : queryMode === 'group' ? '组内待办 (' + group + ')' : queryMode === 'mine' ? '我的待办 (' + currentUser + ')' : '全部待办' }}</b>
          &nbsp;|&nbsp; 共 {{ filteredTasks.length }} / {{ tasks.length }} 条
        </div>
      </div>

      <!-- 兜底：全部待办（只在需要时用） -->
      <div style="margin:6px 0">
        <button class="ghost" @click="showAll">📊 全部待办（查看所有任务）</button>
      </div>

      <!-- 任务列表 -->
      <div v-for="t in filteredTasks" :key="t.taskId" class="task-card" :style="{borderLeftColor: nodeHint(t.nodeName).color}">
        <!-- 标题行 -->
        <div style="display:flex;justify-content:space-between;align-items:center">
          <div>
            <b :style="{color: nodeHint(t.nodeName).color}">{{ nodeHint(t.nodeName).icon }} {{ t.nodeName }}</b>
            <span style="margin-left:8px;font-weight:bold">{{ t.title }}</span>
            <span class="muted">(工单 {{ (t.ticketId||'').slice(-6) }})</span>
            <span class="win-badge" :style="{borderColor: nodeHint(t.nodeName).color, color: nodeHint(t.nodeName).color}">
              审批窗口：{{ taskWindow(t) }}
            </span>
          </div>
          <span class="badge-step" :style="{background: nodeHint(t.nodeName).color+'22', color: nodeHint(t.nodeName).color}">
            第{{ nodeHint(t.nodeName).step }}步
          </span>
        </div>

        <!-- 步骤说明 -->
        <div class="step-desc">{{ nodeHint(t.nodeName).desc }}</div>

        <!-- 任务元信息 -->
        <div class="muted meta-row">
          taskId: <code>{{ t.taskId.slice(0,8) }}...</code>
          &nbsp;|&nbsp;
          处理人: <b>{{ fmt(t.assignee) }}</b>
          &nbsp;|&nbsp;
          当前登录: <b style="color:#4fc3f7">{{ currentUser }}</b>
        </div>

        <!-- 操作按钮 -->
        <div style="margin-top:10px;display:flex;gap:8px;flex-wrap:wrap">
          <!-- 未认领：显示 接单 + 一键接单并通过 -->
          <template v-if="!t.assignee">
            <button @click="doClaim(t)">接单</button>
            <button class="ok" @click="doClaimAndApprove(t)" title="自动接单并立即通过">✅ 一键接单并通过</button>
          </template>
          <!-- 已认领且未进入驳回填写：显示 通过 + 驳回 -->
          <template v-else-if="rejectingTask !== t.taskId">
            <button class="ok" @click="doComplete(t, true)">通过</button>
            <button class="danger" @click="startReject(t)">驳回</button>
          </template>
          <!-- 填写驳回理由 -->
          <template v-else>
            <textarea v-model="rejectReasonInput" rows="2" placeholder="请填写驳回理由（选填，将记录到审计日志并展示给提交人）"
                      style="flex:1;min-width:220px;border-color:#e74c3c;border-radius:6px;padding:6px"></textarea>
            <button class="danger" @click="confirmReject(t)">确认驳回</button>
            <button class="ghost" @click="cancelReject">取消</button>
          </template>
        </div>

        <!-- 驳回理由展示（停在补充材料时提示提交人针对性补材料） -->
        <div v-if="t.rejectReason" class="reject-reason">
          ⚠️ 驳回理由：{{ t.rejectReason }}
        </div>
      </div>

      <div v-if="!filteredTasks.length" class="empty-state">
        <div style="font-size:36px;margin-bottom:8px">🔍</div>
        <div>{{ tasks.length && !filteredTasks.length ? '当前过滤条件下无待办' : '暂无待办' }}</div>
        <div class="muted" style="margin-top:4px;font-size:12px">
          <template v-if="auditFilter">窗口过滤：从全部 {{ tasks.length }} 条任务中筛选 <b>{{ auditFilter === '财务打款' ? '财务打款' : '初审 / 补充材料' }}</b> 节点</template>
          <template v-else>当前用 <b>{{ queryMode === 'group' ? '组内(' + group + ')' : queryMode === 'mine' ? '人名(' + currentUser + ')' : '全部' }}</b> 查询，结果为空。</template><br/>
          提示：点上方按钮切换身份，系统会自动选择正确的查询方式。
        </div>
      </div>
    </section>

    <!-- AI 复核（人在回路） -->
    <section v-show="tab === 'review'" class="panel">
      <div style="display:flex;justify-content:space-between;align-items:center">
        <div>
          <b>AI 人工复核队列</b>
          <span class="muted"> · 置信度低于阈值（{{ '0.55' }}）的工单在此由人定夺，确认后结论回流向量库</span>
        </div>
        <button class="ghost" @click="refreshReview">刷新</button>
      </div>

      <div v-for="t in reviewTickets" :key="t.id" class="task-card" style="borderLeftColor:#e67e22">
        <div style="display:flex;justify-content:space-between;align-items:center">
          <div>
            <b>工单 {{ t.id.slice(-6) }}</b>
            <span style="margin-left:8px;font-weight:bold">{{ t.title }}</span>
          </div>
          <span :class="['tag', catClass(t.category)]">{{ t.category }}</span>
        </div>

        <!-- AI 解释条：展示判定依据 -->
        <AiExplain v-if="reviewEvidence[t.id]" :result="reviewEvidence[t.id]" />

        <!-- 人工确认 / 改判 -->
        <div style="margin-top:10px;display:flex;gap:8px;flex-wrap:wrap;align-items:center">
          <span class="muted">人工裁定：</span>
          <select v-model="reviewOverride[t.id]" style="width:auto;display:inline-block">
            <option value="">沿用 AI 判定（{{ t.category }}）</option>
            <option value="硬件类">改判为 硬件类</option>
            <option value="财务类">改判为 财务类</option>
            <option value="权限类">改判为 权限类</option>
          </select>
          <button class="ok" @click="doAiConfirm(t)">✅ 确认分派并启动流程</button>
        </div>
      </div>

      <div v-if="!reviewTickets.length" class="empty-state">
        <div style="font-size:36px;margin-bottom:8px">✅</div>
        <div>AI 分派置信度均达标，暂无需要人工复核的工单</div>
        <div class="muted" style="margin-top:4px;font-size:12px">提交一条信息模糊的工单（如“帮忙处理一下”），即可看到它进入此队列</div>
      </div>
    </section>

    <!-- SLA -->
    <section v-show="tab === 'sla'" class="panel">
      <button class="ghost" @click="refreshSla">刷新</button>
      <SlaChart :stats="slaStats" />
      <table style="margin-top:8px">
        <thead><tr><th>节点</th><th>平均时长(分)</th><th>最大时长(分)</th><th>样本数</th><th>状态</th></tr></thead>
        <tbody>
          <tr v-for="s in slaStats" :key="s.nodeName">
            <td>{{ s.nodeName }}</td>
            <td>{{ (s.avgDurationMs/60000).toFixed(1) }}</td>
            <td>{{ (s.maxDurationMs/60000).toFixed(1) }}</td>
            <td>{{ s.count }}</td>
            <td><span :class="['badge', s.slow && 'slow']">{{ s.slow ? '慢节点' : '正常' }}</span></td>
          </tr>
        </tbody>
      </table>
      <div v-if="!slaStats.length" class="muted">暂无流程历史，提交并走完几个工单后即可看到 SLA 数据。</div>
    </section>
  </div>
</template>
