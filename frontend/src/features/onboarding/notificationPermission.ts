export type NotificationState = NotificationPermission | 'unsupported' | 'insecure' | 'install-required' | 'error'
export type NotificationEnvironment = {
  secure: boolean
  ios: boolean
  standalone: boolean
  notification?: { permission: NotificationPermission; requestPermission: () => Promise<NotificationPermission> }
}

export function readNotificationState(env: NotificationEnvironment): NotificationState {
  if (!env.secure) return 'insecure'
  if (env.ios && !env.standalone) return 'install-required'
  return env.notification?.permission ?? 'unsupported'
}

// 권한 상태는 저장된 UI 값이 아니라 브라우저에서 매번 확인한다.
export function createNotificationPermissionController(getEnvironment: () => NotificationEnvironment) {
  let pending: Promise<NotificationState> | undefined
  function read() { return readNotificationState(getEnvironment()) }
  function request(): Promise<NotificationState> {
    if (pending) return pending
    const env = getEnvironment()
    const state = readNotificationState(env)
    if (state !== 'default') return Promise.resolve(state)
    try {
      // 사용자 클릭 안에서 즉시 요청해야 브라우저의 사용자 동작 조건을 유지한다.
      pending = env.notification!.requestPermission()
        .then(() => read())
        .catch((): NotificationState => 'error')
        .finally(() => { pending = undefined })
      return pending
    } catch {
      return Promise.resolve('error')
    }
  }
  return { read, request }
}
