export type ApiEnvelope<T> = {
  status: 'success'
  data: T
  serverTime: string
}

export type ApiErrorBody = {
  type: string
  code: string
  field?: string | null
  message: string
  retryable?: boolean
}

export type ApiErrorEnvelope = {
  status: 'error'
  error: ApiErrorBody
  serverTime: string
}

export type Notification = {
  notificationId: string
  promiseId: string
  notificationType: string
  scheduledAt: string
  sentAt: string | null
  readAt: string | null
}

export type AuthAttempt = {
  attemptId: string
  toolType: string
  processingStatus: string
  decisionResult: string | null
  rejectionReason: string | null
  technicalErrorType: string | null
  modelVersion: string | null
  requestReceivedAt: string
  validReceivedAt: string | null
  decidedAt: string | null
}

export type ValidationSurvey = {
  id: string
  questionnaireVersion: string
  lifecycleStatus: string
  eligibilityStatus: string
  actualStarted: boolean | null
  startOrder: string | null
  startDelayBucket: string | null
  noStartReason: string | null
  availableAt: string | null
  expiresAt: string | null
  submittedAt: string | null
}

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
