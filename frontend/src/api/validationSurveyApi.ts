import type { ValidationSurvey } from './doduApi'

export type SurveySubmission = {
  actualStarted: boolean
  startOrder: string | null
  startDelayBucket: string | null
  noStartReason: string | null
}
export class SurveyApiError extends Error {
  readonly kind: 'session' | 'http' | 'invalid-response' | 'network' | 'stale'
  readonly status: number
  constructor(kind: SurveyApiError['kind'], status = 0) {
    super(kind)
    this.kind = kind
    this.status = status
  }
}
function isSurvey(value: unknown): value is ValidationSurvey {
  if (!value || typeof value !== 'object') return false
  const row = value as Record<string, unknown>
  const strings = ['id', 'questionnaireVersion', 'lifecycleStatus', 'eligibilityStatus']
  const optional = ['startOrder', 'startDelayBucket', 'noStartReason']
  const dates = ['availableAt', 'expiresAt', 'submittedAt']
  return strings.every(key => typeof row[key] === 'string' && row[key] !== '') &&
    optional.every(key => row[key] === null || typeof row[key] === 'string') &&
    dates.every(key => row[key] === null || (typeof row[key] === 'string' && Number.isFinite(Date.parse(row[key] as string)))) &&
    (row.actualStarted === null || typeof row.actualStarted === 'boolean')
}

export function createValidationSurveyApi(options: {
  getToken: () => string | null
  baseUrl?: string
  fetcher?: typeof fetch
}) {
  async function request(promiseId: string, method: 'GET' | 'POST', payload?: SurveySubmission, skip = false) {
    const token = options.getToken()
    if (!token) throw new SurveyApiError('session')
    if (!promiseId.trim()) throw new Error('promiseId required')
    let response: Response
    try {
      response = await (options.fetcher ?? fetch)(`${options.baseUrl ?? ''}/api/promises/${encodeURIComponent(promiseId)}/validation-survey${skip ? '/skip' : ''}`, {
        method, cache: 'no-store',
        headers: { Authorization: `Bearer ${token}`, Accept: 'application/json', ...(payload ? { 'Content-Type': 'application/json' } : {}) },
        ...(payload ? { body: JSON.stringify(payload) } : {}),
      })
    } catch { throw new SurveyApiError('network') }
    if (options.getToken() !== token) throw new SurveyApiError('stale')
    if (!response.ok) throw new SurveyApiError('http', response.status)
    let body: unknown
    try { body = await response.json() } catch { throw new SurveyApiError('invalid-response') }
    if (options.getToken() !== token) throw new SurveyApiError('stale')
    // 현재 직접 응답과 공통 성공 응답을 모두 읽되 잘못된 형태를 성공으로 처리하지 않는다.
    if (body && typeof body === 'object' && 'status' in body) {
      const wrapped = body as { status: unknown; data?: unknown }
      if (wrapped.status !== 'success') throw new SurveyApiError('invalid-response')
      body = wrapped.data
    }
    if (!isSurvey(body)) throw new SurveyApiError('invalid-response')
    return body
  }
  return {
    get: (id: string) => request(id, 'GET'),
    submit: (id: string, payload: SurveySubmission) => request(id, 'POST', payload),
    skip: (id: string) => request(id, 'POST', undefined, true),
  }
}
export type ValidationSurveyApi = ReturnType<typeof createValidationSurveyApi>