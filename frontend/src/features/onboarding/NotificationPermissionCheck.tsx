import { useEffect, useRef, useState } from 'react'
import OnboardingNotificationPage from './OnboardingNotificationPage'
import { detectOnboardingPlatform } from './detectOnboardingPlatform'
import { createNotificationPermissionController } from './notificationPermission'
import type { NotificationState } from './notificationPermission'

function environment() {
  const ios = detectOnboardingPlatform() === 'ios'
  return {
    secure: typeof window !== 'undefined' && window.isSecureContext,
    ios,
    standalone: typeof window !== 'undefined' && (
      window.matchMedia('(display-mode: standalone)').matches ||
      (navigator as Navigator & { standalone?: boolean }).standalone === true
    ),
    notification: typeof Notification === 'undefined' ? undefined : {
      permission: Notification.permission,
      requestPermission: () => Notification.requestPermission(),
    },
  }
}

const statusLabels: Record<NotificationState, string> = {
  default: '아직 선택하지 않음', granted: '브라우저 알림 권한 허용', denied: '브라우저 알림 권한 거부',
  unsupported: '알림 API 미지원', insecure: '보안 연결 필요',
  'install-required': 'iPhone 홈 화면 앱에서 확인 필요', error: '권한 확인 오류',
}

// 디자인 화면과 별도로 실제 권한만 확인한다. 서버 구독·온보딩 완료로 기록하지 않는다.
export default function NotificationPermissionCheck({ onClose }: { onClose: () => void }) {
  const [controller] = useState(() => createNotificationPermissionController(environment))
  const [state, setState] = useState(controller.read)
  const [busy, setBusy] = useState(false)
  const [attempt, setAttempt] = useState(0)
  const active = useRef(false)
  const requestVersion = useRef(0)
  const ios = detectOnboardingPlatform() === 'ios'
  useEffect(() => {
    active.current = true
    const refresh = () => {
      if (document.visibilityState === 'visible') setState(controller.read())
    }
    window.addEventListener('focus', refresh)
    document.addEventListener('visibilitychange', refresh)
    return () => {
      active.current = false
      requestVersion.current += 1
      window.removeEventListener('focus', refresh)
      document.removeEventListener('visibilitychange', refresh)
    }
  }, [controller])
  async function request() {
    if (busy) return
    const version = ++requestVersion.current
    setBusy(true)
    const result = await controller.request()
    if (!active.current || version !== requestVersion.current) return
    setState(result)
    setAttempt(value => value + 1)
    setBusy(false)
  }
  return <>
    <aside aria-label="실제 알림 권한 확인" style={{ padding: 16, background: '#ebe9fb' }}>
      <p role="status">실제 브라우저 권한 확인: {statusLabels[state]}</p>
      <p>알림 발송·구독 등록·서버 저장은 포함하지 않습니다. 카메라 요청 시점은 SCRUM-75 확인 후 연결합니다.</p>
      <button type="button" onClick={onClose}>디자인 미리보기로 돌아가기</button>
    </aside>
    <div>
      <OnboardingNotificationPage totalSteps={ios ? 4 : 3} onClose={onClose} onBack={onClose}
        requestDisabled={['granted', 'unsupported', 'insecure', 'install-required'].includes(state)}
        requestBusy={busy}
        onRequest={() => { void request() }} isDenied={state === 'denied'} attempt={attempt}
        recoveryText={ios
          ? '홈 화면의 DODU 앱에서 다시 확인해 주세요. iPhone 설정의 앱별 알림 설정도 확인해 주세요.'
          : '사용 중인 브라우저의 사이트 권한에서 알림을 허용한 뒤 다시 확인해 주세요.'} />
    </div>
  </>
}
