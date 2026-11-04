import type { PushNotice, PushState } from './pushController'
import { pushDestination } from './pushConfig'
import './push.css'

export default function PushConnection({ state, notice, retry, dismiss }: {
  state: PushState; notice: PushNotice | null; retry: () => void; dismiss: () => void
}) {
  if (state === 'error' || state === 'unsupported') return <aside className="push-connection" aria-label="알림 연결">
    <p role="status">{state === 'unsupported' ? '이 브라우저에서는 푸시 알림을 받을 수 없어요.' : '알림을 연결하지 못했어요. 다시 시도해 주세요.'}</p>
    {state === 'error' && <button type="button" onClick={retry}>알림 다시 연결</button>}
  </aside>
  if (!notice) return state === 'ready'
    ? <p className="push-connection" role="status">알림이 연결되어 있어요.</p> : null
  return <aside className="push-connection" aria-label="새 알림">
    <p role="status">Dodu 알림이 도착했어요.</p>
    <a href={pushDestination(notice.data)}>확인하기</a>
    <button type="button" onClick={dismiss}>닫기</button>
  </aside>
}
