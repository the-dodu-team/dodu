import type { PreviewPromise } from '../promise/types'

export type ReportTarget = PreviewPromise & { status: 'verified' | 'unverified' }
export type Started = 'yes' | 'no' | 'unknown'
export type Order = 'before' | 'after' | 'without-auth' | 'unknown'
export type Delay = 'immediate' | 'within-5' | 'within-15' | 'over-15'
export type Reason = 'condition' | 'schedule' | 'motivation' | 'distraction' | 'other'
export type ReportAnswers = {
  started: Started
  order: Order | null
  delay: Delay | null
  reason: Reason | null
}
export type ReportEntry = {
  status: 'pending' | 'submitted' | 'skipped'
  reprompted: boolean
  answers: ReportAnswers
}
export type ReportStore = Record<string, ReportEntry>
export const emptyAnswers: ReportAnswers = {
  started: 'unknown', order: null, delay: null, reason: null,
}
export function reportKey(target: PreviewPromise) {
  return JSON.stringify([target.date, target.startTime, target.taskName, target.tool])
}
export function asReportTarget(promise: PreviewPromise | null, now: number): ReportTarget | null {
  if (!promise) return null
  if (promise.status === 'verified' || promise.status === 'unverified') {
    return { ...promise, status: promise.status }
  }
  const end = Date.parse(`${promise.date}T${promise.startTime}:00+09:00`) + 30 * 60_000
  if (promise.status === 'pending' && Number.isFinite(end) && now >= end) {
    return { ...promise, status: 'unverified' }
  }
  return null
}
export function reportWindow(target: PreviewPromise) {
  const morning = Number(target.startTime.slice(0, 2)) < 12
  const promptedAt = Date.parse(`${target.date}T${morning ? '18' : '11'}:00:00+09:00`) +
    (morning ? 0 : 24 * 60 * 60_000)
  return { promptedAt, expiresAt: promptedAt + 24 * 60 * 60_000 }
}
export function canAnswer(target: ReportTarget, entry: ReportEntry | undefined, now: number) {
  const { promptedAt, expiresAt } = reportWindow(target)
  return Number.isFinite(promptedAt) && now >= promptedAt && now < expiresAt &&
    (!entry || entry.status === 'pending')
}
export function completeAnswers(answers: ReportAnswers) {
  if (answers.started === 'no') return answers.reason !== null
  if (answers.started !== 'yes' || answers.order === null) return false
  return answers.order !== 'after' || answers.delay !== null
}
export function cleanAnswers(answers: ReportAnswers): ReportAnswers {
  if (answers.started === 'no') return { ...emptyAnswers, started: 'no', reason: answers.reason }
  if (answers.started !== 'yes') return { ...emptyAnswers }
  return { ...answers, reason: null, delay: answers.order === 'after' ? answers.delay : null }
}
export function reportStatus(target: ReportTarget, entry: ReportEntry | undefined, now: number) {
  if (entry?.status === 'submitted') return 'submitted'
  if (entry?.status === 'skipped') return 'skipped'
  if (now >= reportWindow(target).expiresAt) return 'expired'
  return 'pending'
}
export type ReportAction =
  | { type: 'seen'; target: ReportTarget; now: number }
  | { type: 'skip'; target: ReportTarget; now: number }
  | { type: 'submit'; target: ReportTarget; now: number; answers: ReportAnswers }
export function reportReducer(store: ReportStore, action: ReportAction): ReportStore {
  const key = reportKey(action.target)
  const previous = store[key]
  if (!canAnswer(action.target, previous, action.now)) return store
  const base: ReportEntry = previous ?? {
    status: 'pending', reprompted: false, answers: { ...emptyAnswers },
  }
  if (action.type === 'seen') {
    if (base.reprompted) return store
    return { ...store, [key]: { ...base, reprompted: true } }
  }
  if (action.type === 'skip') {
    return { ...store, [key]: { ...base, status: 'skipped', answers: { ...emptyAnswers } } }
  }
  if (!completeAnswers(action.answers)) return store
  return { ...store, [key]: {
    ...base, status: 'submitted', answers: cleanAnswers(action.answers),
  } }
}
export function latestTargets(targets: ReportTarget[]) {
  const unique = new Map(targets.map(target => [reportKey(target), target]))
  return [...unique.values()].sort((a, b) =>
    `${b.date}T${b.startTime}`.localeCompare(`${a.date}T${a.startTime}`))
}
export function recordDate(date: string) {
  const value = new Date(`${date}T00:00:00+09:00`)
  const parts = new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'Asia/Seoul', month: 'long', day: 'numeric', weekday: 'short',
  }).formatToParts(value)
  const part = (type: string) => parts.find(item => item.type === type)?.value ?? ''
  const day = part('day')
  return `${part('month')} ${day.endsWith('일') ? day : `${day}일`} (${part('weekday')})`
}
export function reportLabel(target: ReportTarget, full = true) {
  const [hour, minute] = target.startTime.split(':').map(Number)
  return `${recordDate(target.date)} ${hour < 12 ? '오전' : '오후'} ${hour % 12 || 12}:${String(minute).padStart(2, '0')}${full ? ' 시작 약속' : ''} · ${target.taskName}`
}
