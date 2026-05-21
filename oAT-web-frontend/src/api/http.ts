import type { ApiResponse } from './types'

const AUTH_REQUIRED_CODE = 'AUTH_REQUIRED'

let authRedirectPending = false

export class ApiError extends Error {
  status: number
  code?: string

  constructor(message: string, status: number, code?: string) {
    super(message)
    this.status = status
    this.code = code
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
  const response = await fetch(input, {
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      ...(init?.headers || {}),
    },
    ...init,
  })

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
      payload.errorMessage || payload.message || '请求失败',
      response.status,
      payload.errorMessage,
    )
  }
  return payload.data
}

async function requestRawJson<T>(input: string, init?: RequestInit): Promise<T> {
  const response = await fetch(input, {
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      ...(init?.headers || {}),
    },
    ...init,
  })

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
