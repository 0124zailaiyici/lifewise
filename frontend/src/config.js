/**
 * 本地独立模式配置
 * - 业务数据与 AI 调用都在前端完成
 * - SERVER_ORIGIN 默认空；仅当你临时连旧后端时才填写
 */
const rawOrigin = (import.meta.env.VITE_SERVER_ORIGIN || '').trim().replace(/\/$/, '')
export const SERVER_ORIGIN = rawOrigin
export const API_BASE = SERVER_ORIGIN ? `${SERVER_ORIGIN}/api` : '/api'
export const LOCAL_MODE = true

export function resolveAssetUrl(url) {
  if (!url) return ''
  if (/^(https?:|data:|blob:|capacitor:|file:)/i.test(url)) return url
  if (url.startsWith('//')) return `http:${url}`
  if (url.startsWith('/')) return SERVER_ORIGIN ? `${SERVER_ORIGIN}${url}` : url
  return SERVER_ORIGIN ? `${SERVER_ORIGIN}/uploads/${url}` : `/uploads/${url}`
}
