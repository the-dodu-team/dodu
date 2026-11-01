export interface KeyValueStorage {
  getItem(key: string): string | null | Promise<string | null>
  setItem(key: string, value: string): void | Promise<void>
  removeItem(key: string): void | Promise<void>
}

export function createBrowserStorage(storage: Storage): KeyValueStorage {
  return {
    getItem: key => storage.getItem(key),
    setItem: (key, value) => storage.setItem(key, value),
    removeItem: key => storage.removeItem(key),
  }
}

export function createMemoryStorage(initial: Record<string, string> = {}): KeyValueStorage {
  const values = new Map(Object.entries(initial))
  return {
    getItem: key => values.get(key) ?? null,
    setItem: (key, value) => { values.set(key, value) },
    removeItem: key => { values.delete(key) },
  }
}
