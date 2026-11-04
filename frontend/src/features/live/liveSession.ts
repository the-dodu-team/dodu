import type { PromisePayload } from '../../api/participantPromiseApi'

export type PendingWrite =
  | { kind: 'create'; key: string; payload: PromisePayload }
  | { kind: 'reschedule'; id: string; version: number; scheduledAt: string }
  | { kind: 'cancel'; id: string; version: number }
export function createLiveSession(storage: Storage, deviceStorage: Storage) {
  const tokenKey = 'dodu.live.session.v1'
  const pendingKey = 'dodu.live.pending.v1'
  const deviceKey = 'dodu.live.device.v1'
  return {
    token: () => storage.getItem(tokenKey),
    enter(token: string) { storage.setItem(tokenKey, token); storage.removeItem(pendingKey) },
    clear() { storage.removeItem(tokenKey); storage.removeItem(pendingKey) },
    device() {
      let value = deviceStorage.getItem(deviceKey)
      if (!value) { value = crypto.randomUUID(); deviceStorage.setItem(deviceKey, value) }
      return value
    },
    pending(): PendingWrite | null {
      const value = storage.getItem(pendingKey)
      if (!value) return null
      // 현재 탭에 보관한 요청을 복원한다.
      const row = JSON.parse(value) as PendingWrite
      if (!['create', 'reschedule', 'cancel'].includes(row.kind)) throw new Error('INVALID_PENDING_WRITE')
      return row
    },
    mark(value: PendingWrite | null) {
      if (value) storage.setItem(pendingKey, JSON.stringify(value))
      else storage.removeItem(pendingKey)
    },
  }
}
