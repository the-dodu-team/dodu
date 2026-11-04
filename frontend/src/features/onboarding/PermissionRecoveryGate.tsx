import { useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import ScreenHeader from '../../components/ScreenHeader'
import { requestCameraPermission, cameraRecoveryMessage } from './cameraPermission'
import type { CameraPermissionState } from './cameraPermission'
import { createNotificationPermissionController } from './notificationPermission'
import { detectOnboardingPlatform } from './detectOnboardingPlatform'

function environment() {
  return { secure: window.isSecureContext, ios: detectOnboardingPlatform() === 'ios',
    standalone: window.matchMedia('(display-mode: standalone)').matches || (navigator as Navigator & { standalone?: boolean }).standalone === true,
    notification: typeof Notification === 'undefined' ? undefined : {
      permission: Notification.permission, requestPermission: () => Notification.requestPermission(),
    } }
}

// The server's onboarding flag is a history record, never proof of current device permissions.
export default function PermissionRecoveryGate({ children }: { children: ReactNode }) {
  const [controller] = useState(() => createNotificationPermissionController(environment))
  const [notification, setNotification] = useState(controller.read)
  const [camera, setCamera] = useState<CameraPermissionState>('default')
  const [busy, setBusy] = useState(false)
  const [entered, setEntered] = useState(false)
  const pending = useRef(false)
  const generation = useRef(0)
  const content = useRef<HTMLDivElement>(null)
  useEffect(() => {
    let disposed = false, cameraStatus: PermissionStatus | undefined
    async function read() {
      const version = ++generation.current
      setNotification(controller.read())
      if (!window.isSecureContext) { setCamera('insecure'); return }
      if (!navigator.mediaDevices?.getUserMedia) { setCamera('unsupported'); return }
      try {
        const status = await navigator.permissions.query({ name: 'camera' as PermissionName })
        if (disposed || version !== generation.current) return
        cameraStatus?.removeEventListener('change', refresh)
        cameraStatus = status; status.addEventListener('change', refresh)
        setCamera(status.state === 'prompt' ? 'default' : status.state)
      } catch {
        // Unsupported query APIs cannot prove permission. Recheck through a user gesture.
        if (!disposed && version === generation.current) setCamera('default')
      }
    }
    function refresh() { if (!disposed && document.visibilityState === 'visible' && !pending.current) void read() }
    refresh()
    window.addEventListener('focus', refresh); window.addEventListener('pageshow', refresh)
    document.addEventListener('visibilitychange', refresh)
    const timer = window.setInterval(() => { if (!disposed && document.visibilityState === 'visible') setNotification(controller.read()) }, 1000)
    return () => {
      disposed = true; generation.current++
      window.clearInterval(timer)
      cameraStatus?.removeEventListener('change', refresh)
      window.removeEventListener('focus', refresh); window.removeEventListener('pageshow', refresh)
      document.removeEventListener('visibilitychange', refresh)
    }
  }, [controller])
  const ready = notification === 'granted' && camera === 'granted'
  useEffect(() => {
    if (!ready) return
    setEntered(true)
    const frame = requestAnimationFrame(() => {
      const target = content.current?.querySelector<HTMLElement>('[role="dialog"][aria-modal="true"]')
        ?? content.current?.querySelector<HTMLElement>('h1')
      if (target) { target.tabIndex = -1; target.focus() }
    })
    return () => cancelAnimationFrame(frame)
  }, [ready])
  async function check() {
    if (pending.current) return
    pending.current = true; setBusy(true)
    const version = ++generation.current
    try {
      const result = await controller.request()
      if (version !== generation.current) return
      setNotification(result)
      if (result !== 'granted') return
      const state = await requestCameraPermission({ secure: window.isSecureContext,
        open: navigator.mediaDevices?.getUserMedia?.bind(navigator.mediaDevices) })
      if (version === generation.current) setCamera(state)
    } finally { pending.current = false; setBusy(false) }
  }
  const message = notification === 'granted' ? camera === 'default' ? '카메라 권한을 확인한 뒤 계속할 수 있어요.' : cameraRecoveryMessage(camera)
    : notification === 'install-required' ? '공유 메뉴에서 홈 화면에 추가한 뒤 DODU를 열어 주세요.' : notification === 'unsupported'
      ? '알림을 지원하는 브라우저로 접속해 주세요.'
      : notification === 'insecure' ? 'HTTPS 주소로 접속해 주세요.'
      : '브라우저의 사이트 설정에서 알림을 허용한 뒤 다시 확인해 주세요.'
  return <>
    {(entered || ready) && <div ref={content} hidden={!ready} inert={!ready}>{children}</div>}
    {!ready && <main className="screen-page permission-recovery-page">
      <ScreenHeader title="접근 권한 확인" />
      <div className="screen-state-content">
        <h2>{notification !== 'granted' ? '알림 권한이 필요해요' : '카메라 권한이 필요해요'}</h2>
        <p role="status">{message}</p>
        <section className="permission-recovery-guide" aria-label="설정 경로 안내">
          <p>{detectOnboardingPlatform() === 'ios'
            ? 'iPhone 설정과 DODU의 알림·카메라 권한을 확인해 주세요. 홈 화면에 추가한 DODU에서 열어 주세요.'
            : '브라우저 주소창의 사이트 정보 → 사이트 설정 → 알림·카메라 → 허용'}</p>
          <p>설정 메뉴 이름은 OS·브라우저 버전에 따라 다를 수 있어요.</p>
        </section>
      </div>
      <button className="report-primary" type="button" disabled={busy} aria-busy={busy} onClick={() => { void check() }}>
        {busy ? '확인 중…' : '다시 확인'}
      </button>
    </main>}
  </>
}
