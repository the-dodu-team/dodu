import { useRef, useState } from 'react'
import type { ReactNode } from 'react'
import ScreenHeader from '../../components/ScreenHeader'
import { notify } from '../../components/feedback'

export default function SettingsPage({ onBack, onLogout, notificationConnection }: {
  onBack: () => void; onLogout: () => Promise<void>; notificationConnection?: ReactNode
}) {
  const dialog = useRef<HTMLDialogElement>(null)
  const closeButton = useRef<HTMLButtonElement>(null)
  const pending = useRef(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  function open() {
    setError(''); dialog.current?.showModal(); closeButton.current?.focus()
  }
  async function logout() {
    if (pending.current) return
    pending.current = true; setBusy(true); setError('')
    try { await onLogout() }
    catch { const message = '로그아웃하지 못했어요. 연결 상태를 확인한 뒤 다시 시도해 주세요.'; setError(message); notify('error', message) }
    finally { pending.current = false; setBusy(false) }
  }
  return <main className="screen-page settings-page">
    <ScreenHeader title="설정" onBack={onBack} />
    {notificationConnection}
    <button className="settings-logout" type="button" onClick={open}>로그아웃</button>
    <dialog ref={dialog} className="settings-dialog" aria-labelledby="logout-title"
      aria-describedby="logout-description" onCancel={event => { if (busy) event.preventDefault() }}>
      <h2 id="logout-title">로그아웃 할까요?</h2>
      <p id="logout-description">로그아웃 하면 해당 기기 · 브라우저에서는 약속 알림을 받을 수 없어요.</p>
      {error && <p className="settings-error" role="alert">{error}</p>}
      <div className="settings-dialog-actions">
        <button ref={closeButton} className="settings-dialog-close" disabled={busy} onClick={() => dialog.current?.close()}>닫기</button>
        <button className="settings-dialog-confirm" disabled={busy} aria-busy={busy} onClick={() => { void logout() }}>
          {busy ? '로그아웃 중…' : '로그아웃'}
        </button>
      </div>
    </dialog>
  </main>
}
