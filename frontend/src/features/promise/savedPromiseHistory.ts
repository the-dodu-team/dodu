import type { PromiseHistoryRecord } from '../../shared/apiTypes'

export type SavedPromiseHistory = {
  id: string; date: string; scheduledAt: string; taskName: string
  status: 'verified' | 'unverified' | 'cancelled' | 'technical' | 'withdrawn' | 'unknown'
}
export function savedPromiseHistory(rows: PromiseHistoryRecord[]): SavedPromiseHistory[] {
  const states: Record<string, SavedPromiseHistory['status']> = {
    AuthCompleted: 'verified', AuthIncomplete: 'unverified', Cancelled: 'cancelled',
    TechFailed: 'technical', Withdrawn: 'withdrawn', Permission: 'technical',
  }
  return rows.map(row => ({ id: row.promiseId, taskName: row.taskTitle, scheduledAt: row.scheduledAt,
    date: new Date(Date.parse(row.scheduledAt) + 9 * 3600000).toISOString().slice(0, 10),
    status: states[row.finalOutcome ?? row.lifecycleStatus] ?? 'unknown',
  }))
}
