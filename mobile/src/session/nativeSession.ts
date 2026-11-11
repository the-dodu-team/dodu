/** 앱이 주입하는 비동기 저장소. tokenStorage에는 native 보안 저장소만 연결한다. */
export interface AsyncStoragePort {
  getItem(key: string): Promise<string | null>
  setItem(key: string, value: string): Promise<void>
  removeItem(key: string): Promise<void>
}

const tokenKey = 'dodu.native.session.v1'
const deviceKey = 'dodu.native.device.v1'

export function createNativeSession(options: {
  tokenStorage: AsyncStoragePort
  deviceStorage: AsyncStoragePort
  randomUuid: () => string
}) {
  let token: string | null = null
  let device: string | null = null
  let ready = false
  let generation = 0
  let restore: Promise<void> | undefined
  let queue: Promise<void> = Promise.resolve()
  function serial<T>(work: () => Promise<T>): Promise<T> {
    const result = queue.then(work)
    queue = result.then(() => {}, () => {})
    return result
  }
  function requireReady() {
    if (!ready) throw new Error('SESSION_NOT_READY')
  }
  return {
    isReady: () => ready,
    // API 클라이언트의 동기 getToken에 연결하는 메모리 캐시. 복원 전 인증 요청을 시작하지 않는다.
    getToken: () => ready ? token : null,
    getDeviceKey() { requireReady(); return device! },
    initialize(): Promise<void> {
      if (ready) return Promise.resolve()
      if (restore) return restore
      restore = serial(async () => {
        const storedToken = await options.tokenStorage.getItem(tokenKey)
        let storedDevice = await options.deviceStorage.getItem(deviceKey)
        if (!storedDevice) {
          storedDevice = options.randomUuid()
          await options.deviceStorage.setItem(deviceKey, storedDevice)
        }
        token = storedToken; device = storedDevice; ready = true
      }).finally(() => { restore = undefined })
      return restore
    },
    saveToken(value: string): Promise<void> {
      requireReady()
      if (!value.trim()) return Promise.reject(new Error('INVALID_SESSION_TOKEN'))
      const version = generation
      return serial(async () => {
        await options.tokenStorage.setItem(tokenKey, value)
        if (version === generation) token = value
      })
    },
    clear(): Promise<void> {
      requireReady()
      // 호출 즉시 이전 토큰 사용을 막고, 저장 대기 뒤 삭제해 늦은 저장으로 부활하지 않게 한다.
      generation++
      token = null
      return serial(async () => {
        token = null
        await options.tokenStorage.removeItem(tokenKey)
      })
    },
  }
}
