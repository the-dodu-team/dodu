import type { PreviewPromise } from '../promise/types'
import { getFinalPromiseRecord } from '../promise/promiseHistory'

export type HomeState =
  | 'empty'
  | 'scheduled'
  | 'available'
  | 'evaluating'
  | 'ended'
  | 'blocked'

export function interventionClosed(now: number, endsAt?: number) {
  return (
    endsAt !== undefined &&
    Number.isFinite(endsAt) &&
    now >= endsAt
  )
}

export function getHomeState(
  promise: PreviewPromise | null,
  now: number,
): HomeState {
  if (!promise) return 'empty'

  if (promise.status === 'evaluating') return 'evaluating'

  if (
    getFinalPromiseRecord(promise, now) ||
    ['techFailed', 'cancelled'].includes(promise.status)
  ) {
    return 'ended'
  }

  if (['permissionBlocked', 'withdrawn'].includes(promise.status)) {
    return 'blocked'
  }

  const start = Date.parse(
    `${promise.date}T${promise.startTime}:00+09:00`,
  )

  if (!Number.isFinite(start)) return 'blocked'
  if (now < start) return 'scheduled'

  return now < start + 30 * 60_000 ? 'available' : 'blocked'
}