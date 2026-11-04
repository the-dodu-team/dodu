export type PushState = 'disabled' | 'connecting' | 'ready' | 'permission' | 'unsupported' | 'error'
export type PushNotice = { messageId?: string; data?: Record<string, string> }
export type PushDriver = {
  supported: () => Promise<boolean>
  token: () => Promise<string>
  listen: (receive: (notice: PushNotice) => void) => () => void
  remove: () => Promise<void>
}

// Serializes registration with logout. An old async token result must never register a new session.
export function createPushController(options: {
  configured: boolean; missing: boolean
  session: () => string | null; permission: () => string
  driver: () => Promise<PushDriver>
  subscribe: (session: string, token: string) => Promise<unknown>
  state: (state: PushState) => void; receive: (notice: PushNotice) => void
}) {
  let generation = 0, paused = false, driver: PushDriver | undefined
  let pending: Promise<void> | undefined, unlisten: (() => void) | undefined
  let registeredSession = '', registeredToken = ''
  const seen = new Set<string>()
  function disconnect() { unlisten?.(); unlisten = undefined }
  async function perform() {
    const owner = options.session(), version = generation
    if (paused || !owner) return
    if (!options.configured) { options.state(options.missing ? 'error' : 'disabled'); return }
    if (options.permission() !== 'granted') { disconnect(); options.state('permission'); return }
    const current = () => !paused && generation === version && options.session() === owner && options.permission() === 'granted'
    options.state('connecting')
    try {
      driver ??= await options.driver()
      if (!current()) return
      if (!await driver.supported()) { if (current()) options.state('unsupported'); return }
      const token = await driver.token()
      if (!current()) return
      if (!token) throw new Error('PUSH_TOKEN_UNAVAILABLE')
      if (registeredSession !== owner || registeredToken !== token) {
        await options.subscribe(owner, token)
        if (!current()) return
        registeredSession = owner; registeredToken = token
      }
      if (!unlisten) unlisten = driver.listen(notice => {
        if (!current()) return
        if (notice.messageId && seen.has(notice.messageId)) return
        if (notice.messageId) {
          seen.add(notice.messageId)
          if (seen.size > 100) seen.delete(seen.values().next().value!)
        }
        options.receive(notice)
      })
      options.state('ready')
    } catch { if (current()) options.state('error') }
  }
  function sync() {
    if (pending) return pending
    pending = perform().finally(() => { pending = undefined })
    return pending
  }
  return {
    sync,
    resume() { paused = false; void sync() },
    async pause() {
      paused = true; generation++; disconnect(); await pending
      registeredSession = ''; registeredToken = ''; seen.clear()
    },
    async removeToken() { await driver?.remove() },
  }
}
