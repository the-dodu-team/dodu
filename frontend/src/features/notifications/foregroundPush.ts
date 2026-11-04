import type { PushNotice } from './pushController'

// Foreground FCM never displays an OS notification automatically. This security notice
// must remain visible even when the session is cleared before its payload arrives.
export function createForegroundPushHandler(
  show: (title: string, options: NotificationOptions) => Promise<void>,
  receive: (notice: PushNotice) => void,
) {
  const shown = new Set<string>()
  return (notice: PushNotice) => {
    if (notice.data?.type === 'SESSION_REPLACED' && (!notice.messageId || !shown.has(notice.messageId))) {
      if (notice.messageId) {
        shown.add(notice.messageId)
        if (shown.size > 100) shown.delete(shown.values().next().value!)
      }
      // Request display before forwarding into the session-clearing callback.
      // Neither a rejected display nor its completion should block session enforcement.
      try {
        void show('다른 기기에서 로그인했어요', {
          body: '새 기기에서 확인하여 이전 기기의 이용이 종료되었어요.',
          tag: 'dodu-session-change', data: { type: 'SESSION_REPLACED' },
        }).catch(() => {})
      } catch { /* OS/browser display failure cannot prevent revocation. */ }
    }
    receive(notice)
  }
}
