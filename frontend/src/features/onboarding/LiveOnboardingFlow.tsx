import { useEffect, useRef, useState } from 'react'
import OnboardingIntroPage from './OnboardingIntroPage'
import { notify } from '../../components/feedback'
import OnboardingInstallPage from './OnboardingInstallPage'
import OnboardingNotificationPage from './OnboardingNotificationPage'
import OnboardingCameraPage from './OnboardingCameraPage'
import { detectOnboardingPlatform } from './detectOnboardingPlatform'
import { createNotificationPermissionController } from './notificationPermission'
import type { NotificationEnvironment } from './notificationPermission'
import { requestCameraPermission, cameraRecoveryMessage } from './cameraPermission'
import type { CameraPermissionState } from './cameraPermission'

function environment(): NotificationEnvironment {
  return {
    secure: window.isSecureContext,
    ios: detectOnboardingPlatform() === 'ios',
    standalone: window.matchMedia('(display-mode: standalone)').matches ||
      (navigator as Navigator & { standalone?: boolean }).standalone === true,
    notification: typeof Notification === 'undefined' ? undefined : {
      permission: Notification.permission,
      requestPermission: () => Notification.requestPermission(),
    },
  }
}

// 안내를 닫아도 서버 완료 상태나 권한을 임의로 저장하지 않는다.
export default function LiveOnboardingFlow({ onClose, onComplete }: { onClose: () => void; onComplete: () => Promise<void> }) {
  const [platform] = useState(detectOnboardingPlatform)
  const [controller] = useState(() => createNotificationPermissionController(environment))
  const [permission, setPermission] = useState(controller.read)
  const [step, setStep] = useState<'intro' | 'install' | 'notification' | 'camera' | 'complete'>('intro')
  const [camera, setCamera] = useState<CameraPermissionState>('default')
  const [completionError, setCompletionError] = useState('')
  const [busy, setBusy] = useState(false)
  const [attempt, setAttempt] = useState(0)
  const content = useRef<HTMLDivElement>(null)
  const mounted = useRef(false)
  const pending = useRef(false)
  const version = useRef(0)
  const totalSteps = platform === 'ios' ? 4 : 3

  useEffect(() => {
    mounted.current = true
    return () => { mounted.current = false; version.current++ }
  }, [])
  useEffect(() => {
    const heading = content.current?.querySelector('h1')
    heading?.setAttribute('tabindex', '-1')
    heading?.focus()
  }, [step])
  /* 이전 권한 복귀 확인 코드
  useEffect(() => {
    const refresh = () => {
      if (document.visibilityState === 'visible') setPermission(controller.read())
    }
    window.addEventListener('focus', refresh)
    document.addEventListener('visibilitychange', refresh)
    return () => {
      window.removeEventListener('focus', refresh)
      document.removeEventListener('visibilitychange', refresh)
    }
  }, [controller])
  */
  useEffect(() => {
    let disposed = false
    let status: PermissionStatus | undefined
    const refresh = () => {
      if (!disposed && document.visibilityState === 'visible') {
        const current = controller.read()
        setPermission(previous => previous === current ? previous : current)
      }
    }

    // 권한 변경 자체를 감지하고, 창 복귀와 페이지 복원도 함께 확인한다.
    window.addEventListener('focus', refresh)
    window.addEventListener('pageshow', refresh)
    document.addEventListener('visibilitychange', refresh)
    if (navigator.permissions?.query) {
      void navigator.permissions.query({ name: 'notifications' })
        .then(result => {
          if (disposed) return
          status = result
          status.addEventListener('change', refresh)
          refresh()
        })
        .catch(() => {
          // 알림 권한 조회를 지원하지 않으면 아래 주기 확인을 사용한다.
        })
    }

    // 브라우저 설정창에서 돌아올 때 이벤트가 누락되어도 화면 상태를 갱신한다.
    const timer = window.setInterval(refresh, 500)
    refresh()
    return () => {
      disposed = true
      window.clearInterval(timer)
      status?.removeEventListener('change', refresh)
      window.removeEventListener('focus', refresh)
      window.removeEventListener('pageshow', refresh)
      document.removeEventListener('visibilitychange', refresh)
    }
  }, [controller])

  function navigate(next: typeof step) {
    version.current++
    setStep(next)
  }
  useEffect(() => {
    if (step === 'notification' && permission === 'granted') {
      version.current++
      setStep('camera')
    }
  }, [step, permission])
  function notificationStep() {
    const current = controller.read()
    setPermission(current)
    navigate(current === 'granted' ? 'camera' : 'notification')
  }
  async function request() {
    if (pending.current) return
    pending.current = true
    const requestVersion = ++version.current
    setBusy(true)
    const result = await controller.request()
    pending.current = false
    if (!mounted.current) return
    setBusy(false)
    setPermission(result)
    setAttempt(value => value + 1)
    if (version.current === requestVersion && result === 'granted') navigate('camera')
  }
  async function requestCamera() {
    if (pending.current) return
    pending.current = true
    const requestVersion = ++version.current
    setBusy(true)
    const result = await requestCameraPermission({
      secure: window.isSecureContext,
      open: navigator.mediaDevices?.getUserMedia?.bind(navigator.mediaDevices),
    })
    pending.current = false
    if (!mounted.current) return
    setBusy(false)
    if (requestVersion !== version.current) return
    setCamera(result)
    setAttempt(value => value + 1)
    if (result === 'granted') navigate('complete')
  }
  async function complete() {
    if (pending.current) return
    const currentPermission = controller.read()
    if (currentPermission !== 'granted') {
      setPermission(currentPermission); navigate('notification'); return
    }
    pending.current = true; setBusy(true); setCompletionError('')
    try { await onComplete(); notify('success', '준비 상태가 저장됐어요.') }
    catch { if (mounted.current) { setCompletionError('준비 상태를 저장하지 못했어요. 다시 시도해 주세요.'); notify('error', '준비 상태를 저장하지 못했어요. 다시 시도해 주세요.') } }
    finally { pending.current = false; if (mounted.current) setBusy(false) }
  }
  const recovery = platform === 'ios'
    ? '홈 화면의 DODU 앱에서 다시 확인해 주세요. iPhone 설정의 앱별 알림 설정도 확인해 주세요.'
    : '사용 중인 브라우저의 사이트 권한에서 알림을 허용한 뒤 다시 확인해 주세요.'

  return <div ref={content}>
    {step === 'intro' && <OnboardingIntroPage totalSteps={totalSteps} onClose={onClose}
      onNext={() => platform === 'ios' ? navigate('install') : notificationStep()} />}
    {step === 'install' && <OnboardingInstallPage onClose={onClose} onBack={() => navigate('intro')}
      onNext={() => { if (environment().standalone) notificationStep() }} />}
    {step === 'notification' && <OnboardingNotificationPage totalSteps={totalSteps} onClose={onClose}
      onBack={() => navigate(platform === 'ios' ? 'install' : 'intro')}
      onRequest={() => { void request() }} requestBusy={busy}
      requestDisabled={['granted', 'unsupported', 'insecure', 'install-required'].includes(permission)}
      isDenied={permission !== 'default' && permission !== 'granted'} recoveryText={
        permission === 'unsupported' ? '이 브라우저에서는 알림 권한을 요청할 수 없어요. 알림을 지원하는 브라우저로 접속해 주세요.'
        : permission === 'insecure' ? '보안 연결(HTTPS)에서 다시 접속해 주세요.'
        : permission === 'install-required' ? '공유 메뉴에서 홈 화면에 추가한 뒤 DODU를 열어 주세요.' : recovery} attempt={attempt} />}
    {(step === 'camera' || step === 'complete') && <OnboardingCameraPage totalSteps={totalSteps}
      isComplete={step === 'complete'} isDenied={!['default', 'granted'].includes(camera)}
      recoveryText={cameraRecoveryMessage(camera)} attempt={attempt} requestDisabled={busy} requestBusy={busy}
      errorMessage={completionError}
      onBack={() => navigate('notification')} onClose={onClose}
      onRequest={step === 'complete' ? () => { void complete() } : () => { void requestCamera() }} />}
  </div>
}
