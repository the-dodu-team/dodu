export interface KeyValueStorage {
  getItem(key: string): string | null | Promise<string | null>
  setItem(key: string, value: string): void | Promise<void>
  removeItem(key: string): void | Promise<void>
}

/** 동기 세션 캐시용 계약. 비동기 Secure Storage를 이 타입으로 강제 변환하지 않는다. */
export interface SyncKeyValueStorage extends KeyValueStorage {
  getItem(key: string): string | null
  setItem(key: string, value: string): void
  removeItem(key: string): void
}

export function createBrowserStorage(storage: SyncKeyValueStorage): SyncKeyValueStorage {
  return {
    getItem: key => storage.getItem(key),
    setItem: (key, value) => storage.setItem(key, value),
    removeItem: key => storage.removeItem(key),
  }
}

export function createMemoryStorage(initial: Record<string, string> = {}): SyncKeyValueStorage {
  const values = new Map(Object.entries(initial))
  return {
    getItem: key => values.get(key) ?? null,
    setItem: (key, value) => { values.set(key, value) },
    removeItem: key => { values.delete(key) },
  }
}
