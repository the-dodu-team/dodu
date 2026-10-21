import { useState } from 'react'
import PhotoAuthMockFlow from './PhotoAuthMockFlow'
import { createPhotoSession } from './photoAuthModel'
import type { PhotoStage } from './photoAuthModel'
import { usePhotoAuthMock } from './usePhotoAuthMock'
import type { PromiseTool } from '../promise/types'

const screens: { value: PhotoStage; label: string }[] = [
  { value: 'ready', label: '촬영 준비' }, { value: 'preview', label: '사진 확인' },
  { value: 'evaluating', label: '판정 대기' }, { value: 'success', label: '인증 완료' },
  { value: 'rejected', label: '사진 거절' }, { value: 'technical-error', label: '기술오류' },
  { value: 'expired', label: '시간 종료' },
  { value: 'tech-failed-recoverable', label: '기술 실패 · 복구 가능' },
  { value: 'tech-failed-unavailable', label: '기술 실패 · 복구 불가' },
]

function Fixture({ stage, tool }: { stage: PhotoStage; tool: PromiseTool }) {
  const [initial] = useState(() => {
    const time = Math.floor(Date.now() / 60_000) * 60_000 - (stage === 'expired' ? 31 : 12) * 60_000
    const kst = new Date(time + 9 * 60 * 60_000).toISOString()
    const session = createPhotoSession({ taskName: '포트폴리오 정리', date: kst.slice(0, 10),
      startTime: kst.slice(11, 16), tool, status: 'pending' })
    return { ...session, stage, attempt: stage === 'ready' ? 0 : 1,
      receivedAt: ['evaluating', 'success', 'rejected', 'technical-error'].includes(stage) ? Date.now() - 2000 : null,
      resolvedAt: stage === 'success' ? Date.now() : null, outcome: 'hold' as const,
      recoveryAllowed: stage === 'tech-failed-recoverable' }
  })
  const { session, dispatch } = usePhotoAuthMock(initial)
  if (!session) return null
  return <PhotoAuthMockFlow session={session} dispatch={dispatch}
    onClose={() => { window.location.search = '' }}
    onRecover={() => { window.location.search = '?preview=promise-name' }} />
}

export default function PhotoAuthPreview() {
  const [stage, setStage] = useState<PhotoStage>('ready')
  const [tool, setTool] = useState<PromiseTool>('computer')
  if (!import.meta.env.DEV) return null
  return <>
    <aside className="photo-mock-controls" aria-label="사진 인증 화면 미리보기 설정">
      <label>미리보기 화면<select value={stage} onChange={event => {
        const selected = screens.find(screen => screen.value === event.target.value)
        if (selected) setStage(selected.value)
      }}>{screens.map(screen => <option key={screen.value} value={screen.value}>{screen.label}</option>)}</select></label>
      <label>인증 도구<select value={tool} onChange={event => setTool(event.target.value === 'book' ? 'book' : 'computer')}>
        <option value="computer">컴퓨터</option><option value="book">책</option>
      </select></label>
    </aside>
    <Fixture key={`${stage}/${tool}`} stage={stage} tool={tool} />
  </>
}
