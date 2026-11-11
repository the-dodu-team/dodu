import type { PromisePayload } from '../../shared/apiTypes'
import type { SyncKeyValueStorage } from '../../platform/storage'

export type PendingWrite =
  | { kind: 'create'; key: string; payload: PromisePayload }
  | { kind: 'reschedule'; id: string; version: number; scheduledAt: string }
  | { kind: 'cancel'; id: string; version: number }
export function createLiveSession(storage: SyncKeyValueStorage, deviceStorage: SyncKeyValueStorage,
  randomUuid: () => string = () => crypto.randomUUID()) {
  const tokenKey = 'dodu.live.session.v1'
  const pendingKey = 'dodu.live.pending.v1'
  const deviceKey = 'dodu.live.device.v1'
  const migrationKey = 'dodu.live.session.migrated.v1'
  // 서버 발급 토큰만 보관한다. 이전 탭의 토큰은 한 번만 이전한다.
  if (!deviceStorage.getItem(migrationKey)) {
    const previous = storage.getItem(tokenKey) ?? deviceStorage.getItem('dodu.participant.session-token')
    if (!deviceStorage.getItem(tokenKey) && previous) deviceStorage.setItem(tokenKey, previous)
    deviceStorage.setItem(migrationKey, '1')
  }
  storage.removeItem(tokenKey)
  deviceStorage.removeItem('dodu.participant.session-token')
  return {
    token: () => deviceStorage.getItem(tokenKey),
    enter(token: string) { deviceStorage.setItem(tokenKey, token); storage.removeItem(pendingKey) },
    clear() { deviceStorage.removeItem(tokenKey); storage.removeItem(tokenKey); storage.removeItem(pendingKey); deviceStorage.removeItem('dodu.participant.session-token') },
    device() {
      let value = deviceStorage.getItem(deviceKey) ?? deviceStorage.getItem('dodu.participant.device-key')
      if (!value) { value = randomUuid(); deviceStorage.setItem(deviceKey, value) }
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
