import type {
  AccessResponse, ApiEnvelope, ApiErrorEnvelope, AuthAttempt, Notification, PhotoAttempt,
  PromiseHistoryRecord, PromiseRecord, ValidationSurvey,
} from '../shared/apiTypes'

export type {
  AccessResponse, ApiEnvelope, ApiErrorBody, ApiErrorEnvelope, AuthAttempt, Notification,
  PhotoAttempt, PromiseHistoryRecord, PromiseRecord, ValidationSurvey,
} from '../shared/apiTypes'

export class DoduApiError extends Error {
  readonly response: ApiErrorEnvelope
  readonly httpStatus: number

  constructor(response: ApiErrorEnvelope, httpStatus: number) {
    super(response.error.message)
    this.response = response
    this.httpStatus = httpStatus
    this.name = 'DoduApiError'
  }
}

export class DoduRequestTimeout extends Error {
  constructor() {
    super('REQUEST_TIMEOUT')
    this.name = 'DoduRequestTimeout'
  }
}

// Bound both connection time and response-body reading; never retry writes automatically.
export async function fetchDoduJson<T>(url: string, init: RequestInit = {}, timeoutMs = 60_000) {
  const controller = new AbortController()
  let timer: ReturnType<typeof setTimeout> | undefined
  const forwardAbort = () => controller.abort(init.signal?.reason)
  if (init.signal?.aborted) forwardAbort()
  else init.signal?.addEventListener('abort', forwardAbort, { once: true })
  try {
    return await Promise.race([
      (async () => {
        const response = await fetch(url, { ...init, signal: controller.signal })
        const text = await response.text()
        const body = (text.trim() ? JSON.parse(text) : null) as T
        return { response, body }
      })(),
      new Promise<never>((_, reject) => {
        timer = setTimeout(() => {
          reject(new DoduRequestTimeout())
          controller.abort()
        }, timeoutMs)
      }),
    ])
  } finally {
    clearTimeout(timer)
    init.signal?.removeEventListener('abort', forwardAbort)
  }
}

export function createDoduApi(token: string, baseUrl = '') {
  async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const { response, body } = await fetchDoduJson<ApiEnvelope<T> | ApiErrorEnvelope>(`${baseUrl}${path}`, {
      ...init,
      headers: {
        Accept: 'application/json',
        Authorization: `Bearer ${token}`,
        ...(init.body && !(init.body instanceof FormData) ? { 'Content-Type': 'application/json' } : {}),
        ...init.headers,
      },
    })
    if (!response.ok || body.status === 'error') {
      throw new DoduApiError(body as ApiErrorEnvelope, response.status)
    }
    return (body as ApiEnvelope<T>).data
  }

  async function requestRaw<T>(path: string, init: RequestInit = {}): Promise<T> {
    const { response, body } = await fetchDoduJson<T | ApiErrorEnvelope>(`${baseUrl}${path}`, {
      ...init,
      headers: {
        Accept: 'application/json',
        Authorization: `Bearer ${token}`,
        ...(init.body && !(init.body instanceof FormData) ? { 'Content-Type': 'application/json' } : {}),
        ...init.headers,
      },
    })
    if (!response.ok || (body as ApiErrorEnvelope | null)?.status === 'error') {
      throw new DoduApiError(body as ApiErrorEnvelope, response.status)
    }
    return body as T
  }

  return {
    getActivePromise: () => requestRaw<PromiseRecord | null>('/api/promises/active'),
    listPromiseHistory: () => requestRaw<PromiseHistoryRecord[]>('/api/promises/history'),
    createPromise: (payload: {
      taskTitle: string
      toolType: 'COMPUTER' | 'BOOK'
      scheduledAt: string
    }, idempotencyKey: string) => requestRaw<PromiseRecord>('/api/promises', {
      method: 'POST',
      headers: { 'Idempotency-Key': idempotencyKey },
      body: JSON.stringify(payload),
    }),
    reschedulePromise: (promiseId: string, scheduledAt: string, expectedVersion: number) =>
      requestRaw<PromiseRecord>(`/api/promises/${promiseId}/schedule`, {
        method: 'PATCH', body: JSON.stringify({ scheduledAt, expectedVersion }),
      }),
    cancelPromise: (promiseId: string, expectedVersion: number) =>
      requestRaw<PromiseRecord>(`/api/promises/${promiseId}/cancel`, {
        method: 'POST', body: JSON.stringify({ expectedVersion }),
      }),
    submitPhoto: (promiseId: string, photo: File, toolType: 'COMPUTER' | 'BOOK', clientRequestId = crypto.randomUUID()) => {
      const form = new FormData()
      form.set('clientRequestId', clientRequestId)
      form.set('toolType', toolType)
      form.set('photo', photo)
      return requestRaw<PhotoAttempt>(`/api/promises/${promiseId}/photo-attempts`, {
        method: 'POST', body: form,
      })
    },
    withdraw: () => request<unknown>('/api/participant/account', { method: 'DELETE' }),
    listNotifications: () => request<Notification[]>('/api/participant/notifications'),
    markNotificationRead: (notificationId: string) =>
      request<Notification>(`/api/participant/notifications/${notificationId}/read`, { method: 'PUT' }),
    listAuthAttempts: (promiseId: string) =>
      request<AuthAttempt[]>(`/api/promises/${promiseId}/auth-attempts`),
    getValidationSurvey: (promiseId: string) =>
      request<ValidationSurvey>(`/api/promises/${promiseId}/validation-survey`),
    skipValidationSurvey: (promiseId: string) =>
      request<ValidationSurvey>(`/api/promises/${promiseId}/validation-survey/skip`, { method: 'POST' }),
    submitValidationSurvey: (promiseId: string, payload: {
      actualStarted: boolean
      startOrder?: string | null
      startDelayBucket?: string | null
      noStartReason?: string | null
    }) => request<ValidationSurvey>(`/api/promises/${promiseId}/validation-survey`, {
      method: 'POST', body: JSON.stringify(payload),
    }),
  }
}

export function createDoduAccessApi(baseUrl = '') {
  return {
    enter: async (code: string, deviceKey: string): Promise<AccessResponse> => {
      const { response, body } = await fetchDoduJson<ApiEnvelope<AccessResponse> | ApiErrorEnvelope>(`${baseUrl}/api/participant/sessions`, {
        method: 'POST',
        headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
        body: JSON.stringify({ code, deviceKey }),
      })
      if (!response.ok || body.status === 'error') {
        throw new DoduApiError(body as ApiErrorEnvelope, response.status)
      }
      return (body as ApiEnvelope<AccessResponse>).data
    },
  }
}
