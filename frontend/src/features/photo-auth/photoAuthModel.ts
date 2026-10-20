import type { PreviewPromise } from '../promise/types'

export type PhotoStage = 'ready' | 'preview' | 'evaluating' | 'success' | 'rejected'
  | 'technical-error' | 'tech-failed-recoverable' | 'tech-failed-unavailable' | 'expired'
export type MockOutcome = 'success' | 'rejected' | 'technical-error' | 'hold'
export type PhotoSession = {
  promise: PreviewPromise
  stage: PhotoStage
  startsAt: number
  endsAt: number
  attempt: number
  receivedAt: number | null
  resolvedAt: number | null
  resolveAt: number | null
  outcome: MockOutcome
  recoveryAllowed: boolean
}
export type PhotoAction =
  | { type: 'open'; promise: PreviewPromise }
  | { type: 'capture' | 'retake' | 'submit' | 'tick'; now: number }
  | { type: 'outcome'; value: MockOutcome }
  | { type: 'recovery'; allowed: boolean }
  | { type: 'result'; outcome: Exclude<MockOutcome, 'hold'>; attempt: number; now: number }
  | { type: 'clear' }

export function promiseKey(promise: PreviewPromise) {
  return `${promise.date}/${promise.startTime}/${promise.taskName}`
}

export function createPhotoSession(promise: PreviewPromise): PhotoSession {
  const startsAt = Date.parse(`${promise.date}T${promise.startTime}:00+09:00`)
  return { promise, startsAt, endsAt: startsAt + 30 * 60_000, stage: 'ready',
    attempt: 0, receivedAt: null, resolvedAt: null, resolveAt: null,
    outcome: 'success', recoveryAllowed: false }
}

export function canSubmitPhoto(session: PhotoSession, now: number) {
  return Number.isFinite(session.startsAt) && now >= session.startsAt && now < session.endsAt
}

function technicalEnd(session: PhotoSession): PhotoSession {
  return { ...session, resolveAt: null, stage: session.recoveryAllowed
    ? 'tech-failed-recoverable' : 'tech-failed-unavailable' }
}

function applyResult(session: PhotoSession, outcome: Exclude<MockOutcome, 'hold'>, now: number): PhotoSession {
  if (session.receivedAt === null || !canSubmitPhoto(session, session.receivedAt)) return session
  const next = { ...session, resolvedAt: now, resolveAt: null }
  if (outcome === 'success') return { ...next, stage: 'success' }
  if (outcome === 'technical-error') return now >= session.endsAt
    ? technicalEnd(next) : { ...next, stage: 'technical-error' }
  return { ...next, stage: now >= session.endsAt ? 'expired' : 'rejected' }
}

export function photoReducer(session: PhotoSession | null, action: PhotoAction): PhotoSession | null {
  if (action.type === 'clear') return null
  if (action.type === 'open') {
    return session && promiseKey(session.promise) === promiseKey(action.promise)
      ? session : createPhotoSession(action.promise)
  }
  if (!session) return null
  if (action.type === 'outcome') return session.stage === 'evaluating'
    ? session : { ...session, outcome: action.value }
  if (action.type === 'recovery') {
    const next = { ...session, recoveryAllowed: action.allowed }
    return session.stage.startsWith('tech-failed-') ? technicalEnd(next) : next
  }
  if (action.type === 'result') {
    if (session.stage !== 'evaluating' || action.attempt !== session.attempt) return session
    return applyResult(session, action.outcome, action.now)
  }
  if (action.type === 'tick') {
    // 접수한 사진은 마감 뒤에도 결과를 기다린다. 재진입으로 종료시각을 갱신하지 않는다.
    if (session.stage === 'evaluating') {
      return session.outcome !== 'hold' && session.resolveAt !== null && action.now >= session.resolveAt
        ? applyResult(session, session.outcome, action.now) : session
    }
    if (action.now < session.endsAt) return session
    if (session.stage === 'technical-error') return technicalEnd(session)
    return ['ready', 'preview', 'rejected'].includes(session.stage)
      ? { ...session, stage: 'expired' } : session
  }
  if (!canSubmitPhoto(session, action.now)) return photoReducer(session, { type: 'tick', now: action.now })
  if (action.type === 'capture' && session.stage === 'ready') {
    return { ...session, stage: 'preview', attempt: session.attempt + 1,
      receivedAt: null, resolvedAt: null, resolveAt: null }
  }
  if (action.type === 'retake' && ['preview', 'rejected'].includes(session.stage)) {
    return { ...session, stage: 'ready', receivedAt: null, resolvedAt: null, resolveAt: null }
  }
  if (action.type === 'submit' && ['preview', 'technical-error'].includes(session.stage)) {
    // 기술오류 재전송은 같은 시도를 사용하고, 재촬영한 사진만 새 시도를 사용한다.
    return { ...session, stage: 'evaluating', receivedAt: action.now, resolvedAt: null,
      resolveAt: session.outcome === 'hold' ? null : action.now + 1500 }
  }
  return session
}

export function photoPromiseStatus(session: PhotoSession): PreviewPromise['status'] {
  switch (session.stage) {
    case 'success': return 'verified'
    case 'expired': return 'unverified'
    case 'evaluating': return 'evaluating'
    case 'technical-error': return 'techError'
    case 'tech-failed-recoverable':
    case 'tech-failed-unavailable': return 'techFailed'
    default: return 'pending'
  }
}
