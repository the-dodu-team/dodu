import type { ValidationSurvey } from './doduApi'
import { createValidationSurveyApi, SurveyApiError } from './validationSurveyApi'
import type { ReportAnswers } from '../features/self-report/selfReportModel'

export function encodeLiveAnswers(answers: ReportAnswers) {
  if (answers.started === 'unknown') throw new Error('Incomplete answers')
  return {
    actualStarted: answers.started === 'yes',
    startOrder: answers.started === 'yes' ? answers.order : null,
    startDelayBucket: answers.started === 'yes' && answers.order === 'after' ? answers.delay : null,
    noStartReason: answers.started === 'no' ? answers.reason : null,
  }
}

export function canOpenLiveSurvey(row: ValidationSurvey, now: number) {
  return ['AuthCompleted', 'AuthIncomplete'].includes(row.eligibilityStatus) &&
    ['Pending', 'Prompted', 'Reminded'].includes(row.lifecycleStatus) &&
    row.availableAt !== null && row.expiresAt !== null &&
    now >= Date.parse(row.availableAt) && now < Date.parse(row.expiresAt)
}

export function createLiveSurveyApi(getToken: () => string | null, fetcher: typeof fetch = fetch) {
  const survey = createValidationSurveyApi({ getToken, fetcher })
  let offset = 0
  const checkedFetch: typeof fetch = async (input, init) => {
    const response = await fetcher(input, init)
    const date = response.headers.get('Date')
    if (date && Number.isFinite(Date.parse(date))) offset = Date.parse(date) - Date.now()
    return response
  }
  const timedSurvey = createValidationSurveyApi({ getToken, fetcher: checkedFetch })
  return {
    ...survey,
    ...timedSurvey,
    now: () => Date.now() + offset,
    async candidates(): Promise<string[]> {
      const token = getToken()
      if (!token) throw new SurveyApiError('session')
      const response = await checkedFetch('/api/participant/notifications', {
        headers: { Authorization: 'Bearer ' + token, Accept: 'application/json' },
        cache: 'no-store', redirect: 'error',
      })
      if (token !== getToken()) throw new SurveyApiError('stale')
      if (!response.ok) throw new SurveyApiError('http', response.status)
      const body: unknown = await response.json()
      if (token !== getToken()) throw new SurveyApiError('stale')
      if (!body || typeof body !== 'object') throw new SurveyApiError('invalid-response')
      const envelope = body as { status?: unknown; data?: unknown; serverTime?: unknown }
      if (envelope.status !== 'success' || !Array.isArray(envelope.data)) throw new SurveyApiError('invalid-response')
      if (typeof envelope.serverTime === 'string' && Number.isFinite(Date.parse(envelope.serverTime))) {
        offset = Date.parse(envelope.serverTime) - Date.now()
      }
      const ids = envelope.data.filter((row): row is { promiseId: string; notificationType: string } =>
        row !== null && typeof row === 'object' &&
        typeof row.promiseId === 'string' && typeof row.notificationType === 'string')
        .filter(row => row.notificationType === 'SURVEY_REMINDER').map(row => row.promiseId)
      return [...new Set(ids)]
    },
  }
}
export type LiveSurveyApi = ReturnType<typeof createLiveSurveyApi>