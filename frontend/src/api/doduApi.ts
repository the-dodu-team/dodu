import type {
  ApiEnvelope, ApiErrorEnvelope, AuthAttempt, Notification, ValidationSurvey,
} from '../shared/apiTypes'

export type {
  ApiEnvelope, ApiErrorBody, ApiErrorEnvelope, AuthAttempt, Notification, ValidationSurvey,
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

export function createDoduApi(token: string, baseUrl = '') {
  async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const response = await fetch(`${baseUrl}${path}`, {
      ...init,
      headers: {
        Accept: 'application/json',
        Authorization: `Bearer ${token}`,
        ...(init.body ? { 'Content-Type': 'application/json' } : {}),
        ...init.headers,
      },
    })
    const body = await response.json() as ApiEnvelope<T> | ApiErrorEnvelope
    if (!response.ok || body.status === 'error') {
      throw new DoduApiError(body as ApiErrorEnvelope, response.status)
    }
    return (body as ApiEnvelope<T>).data
  }

  return {
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
