import { useEffect, useRef, useState } from 'react'
import { feedbackEvent } from './feedback'
import type { FeedbackKind } from './feedback'
import './feedback.css'

export default function ToastHost() {
  const [notice, setNotice] = useState<{ kind: FeedbackKind; message: string; id: number } | null>(null)
  const region = useRef<HTMLDivElement>(null)
  useEffect(() => {
    const element = region.current
    if (!element?.showPopover) return
    if (notice) element.showPopover()
    else element.hidePopover()
  }, [notice])
  useEffect(() => {
    const receive = (event: Event) => {
      const detail = (event as CustomEvent).detail
      if (!detail || !['success', 'error'].includes(detail.kind) || typeof detail.message !== 'string') return
      setNotice({ kind: detail.kind, message: detail.message, id: performance.now() })
    }
    const unexpected = () => setNotice({ kind: 'error', message: '요청을 처리하지 못했어요. 진행 중인 작업의 결과를 확인해 주세요.', id: performance.now() })
    window.addEventListener(feedbackEvent, receive)
    // 마지막 방어선이다. 원문을 표시하거나 자동 재전송하지 않는다.
    window.addEventListener('unhandledrejection', unexpected)
    const scriptError = (event: ErrorEvent) => { if (event.error) unexpected() }
    window.addEventListener('error', scriptError)
    return () => {
      window.removeEventListener(feedbackEvent, receive)
      window.removeEventListener('unhandledrejection', unexpected)
      window.removeEventListener('error', scriptError)
    }
  }, [])
  useEffect(() => {
    if (!notice || notice.kind === 'error') return
    const timer = window.setTimeout(() => setNotice(current => current?.id === notice.id ? null : current), 5000)
    return () => window.clearTimeout(timer)
  }, [notice])
  return <div ref={region} popover="manual" className="toast-region" aria-label="처리 결과">
    <div role="status" aria-live="polite" aria-atomic="true">
      {notice?.kind === 'success' && <div className="feedback-toast is-success"><span>{notice.message}</span><button type="button" aria-label="성공 안내 닫기" onClick={() => setNotice(null)}>닫기</button></div>}
    </div>
    <div role="alert" aria-atomic="true">
      {notice?.kind === 'error' && <div className="feedback-toast is-error"><span>{notice.message}</span><button type="button" aria-label="실패 안내 닫기" onClick={() => setNotice(null)}>닫기</button></div>}
    </div>
  </div>
}
