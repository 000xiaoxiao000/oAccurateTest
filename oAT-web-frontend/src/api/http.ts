import type { ApiResponse } from './types'

const AUTH_REQUIRED_CODE = 'AUTH_REQUIRED'
const BACKEND_BASE_URL = (import.meta.env.VITE_OAT_BACKEND_BASE_URL || '').replace(/\/$/, '')

let authRedirectPending = false

const API_ERROR_MESSAGES: Record<string, string> = {
  AUTH_REQUIRED: '未登录或登录已过期',
  LOGIN_FAILED: '用户名或密码错误，请重新输入',
  LOGIN_INVALID: '请输入用户名和密码',
  REGISTER_INVALID: '请完整填写注册信息',
  REGISTER_PASSWORD_MISMATCH: '两次输入的密码不一致',
}

export class ApiError extends Error {
  status: number
  code?: string

  constructor(message: string, status: number, code?: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

function backendUrl(input: string) {
  if (/^https?:\/\//.test(input)) {
    return input
  }

  const normalized = input.startsWith('/') ? input : `/${input}`
  return BACKEND_BASE_URL ? `${BACKEND_BASE_URL}${normalized}` : normalized
}

export function formatApiErrorMessage(codeOrMessage?: string, fallback = '请求失败') {
  if (!codeOrMessage) {
    return fallback
  }

  return API_ERROR_MESSAGES[codeOrMessage] || codeOrMessage
}

function resolveApiErrorMessage<T>(payload: ApiResponse<T>) {
  if (payload.errorMessage && API_ERROR_MESSAGES[payload.errorMessage]) {
    return API_ERROR_MESSAGES[payload.errorMessage]
  }

  return payload.message || formatApiErrorMessage(payload.errorMessage)
}

async function fetchApi(input: string, init?: RequestInit) {
  try {
    return await fetch(backendUrl(input), {
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        ...(init?.headers || {}),
      },
      ...init,
    })
  } catch {
    throw new ApiError('无法连接后端服务，请确认后端已启动，并检查前端代理或跨域配置', 0)
  }
}

function redirectToLogin() {
  if (typeof window === 'undefined' || authRedirectPending || window.location.pathname === '/login') {
    return
  }

  authRedirectPending = true
  const redirect = `${window.location.pathname}${window.location.search}${window.location.hash}` || '/projects'
  window.location.assign(`/login?redirect=${encodeURIComponent(redirect)}`)
}

async function request<T>(input: string, init?: RequestInit): Promise<T> {
  const response = await fetchApi(input, init)

  if (response.status === 401) {
    redirectToLogin()
    throw new ApiError('未登录或登录已过期', 401, AUTH_REQUIRED_CODE)
  }

  const rawBody = await response.text()
  let payload: ApiResponse<T>

  try {
    payload = JSON.parse(rawBody) as ApiResponse<T>
  } catch {
    throw new ApiError(
      response.ok ? '服务端返回了非 JSON 响应' : `请求失败，服务端返回了异常响应 (${response.status})`,
      response.status,
    )
  }

  if (!response.ok || !payload.result) {
    throw new ApiError(
      resolveApiErrorMessage(payload),
      response.status,
      payload.errorMessage,
    )
  }
  return payload.data
}

async function requestRawJson<T>(input: string, init?: RequestInit): Promise<T> {
  const response = await fetchApi(input, init)

  if (response.status === 401) {
    redirectToLogin()
    throw new ApiError('未登录或登录已过期', 401, AUTH_REQUIRED_CODE)
  }

  const rawBody = await response.text()
  let payload: T

  try {
    payload = JSON.parse(rawBody) as T
  } catch {
    throw new ApiError(
      response.ok ? '服务端返回了非 JSON 响应' : `请求失败，服务端返回了异常响应 (${response.status})`,
      response.status,
    )
  }

  if (!response.ok) {
    throw new ApiError(`请求失败 (${response.status})`, response.status)
  }
  return payload
}

export function apiGet<T>(input: string): Promise<T> {
  return request<T>(input)
}

export function apiPost<T>(input: string, body?: BodyInit | null, contentType?: string): Promise<T> {
  const headers = contentType ? { 'Content-Type': contentType } : undefined
  return request<T>(input, {
    method: 'POST',
    body,
    headers,
  })
}

export function apiGetRaw<T>(input: string): Promise<T> {
  return requestRawJson<T>(input)
}

export function apiPostRaw<T>(input: string, body?: BodyInit | null, contentType?: string): Promise<T> {
  const headers = contentType ? { 'Content-Type': contentType } : undefined
  return requestRawJson<T>(input, {
    method: 'POST',
    body,
    headers,
  })
}

export function backendApiUrl(input: string) {
  return backendUrl(input)
}
