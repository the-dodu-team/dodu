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
