import type { KeyValueStorage } from './storage'

const SESSION_TOKEN_KEY = 'dodu.participant.session-token'
const DEVICE_KEY = 'dodu.participant.device-key'

export class ParticipantSessionStore {
  private readonly storage: KeyValueStorage

  constructor(storage: KeyValueStorage) {
    this.storage = storage
  }

  async getToken(): Promise<string | null> {
    return this.storage.getItem(SESSION_TOKEN_KEY)
  }

  async saveToken(token: string): Promise<void> {
    await this.storage.setItem(SESSION_TOKEN_KEY, token)
  }

  async getOrCreateDeviceKey(randomUuid: () => string = () => crypto.randomUUID()): Promise<string> {
    const current = await this.storage.getItem(DEVICE_KEY)
    if (current) return current
    const created = randomUuid()
    await this.storage.setItem(DEVICE_KEY, created)
    return created
  }

  async clear(): Promise<void> {
    await Promise.all([
      this.storage.removeItem(SESSION_TOKEN_KEY),
      this.storage.removeItem(DEVICE_KEY),
    ])
  }
}
