import axios from 'axios'

// 直连后端 8080，绕过 Vite dev server 的 /api 代理（代理每次固定多 ~200ms 开销）。
// 后端 CorsConfig 已允许跨域（allowedOriginPatterns("*")），因此 5173 -> 8080 合法。
const http = axios.create({ baseURL: 'http://localhost:8080/api', timeout: 8000 })

export const createTicket = (data) => http.post('/tickets', data)
export const listTickets = (status, category, review) =>
  http.get('/tickets', { params: { status, category, review } })
export const getTicket = (id) => http.get(`/tickets/${id}`)
export const deleteTicket = (id) => http.delete(`/tickets/${id}`)
export const claim = (id, taskId, userId) =>
  http.post(`/tickets/${id}/claim`, null, { params: { taskId, userId } })
export const completeTask = (id, taskId, userId, approved, reason) =>
  http.post(`/tickets/${id}/complete`, null, { params: { taskId, userId, approved, reason: reason || undefined } })
export const suspend = (id) => http.post(`/tickets/${id}/suspend`)
export const resume = (id) => http.post(`/tickets/${id}/resume`)
export const rejectDynamic = (id, target, reason) =>
  http.post(`/tickets/${id}/reject`, null, { params: { target, reason: reason || undefined } })
export const listTasks = (group, assignee) =>
  http.get('/tickets/tasks', { params: { group, assignee } })
export const aiRoute = (title, description) =>
  http.post('/ai/route', { title, description })
export const aiConfirm = (id, category) =>
  http.post(`/tickets/${id}/ai-confirm`, null, { params: { category } })
export const sla = () => http.get('/dashboard/sla')
