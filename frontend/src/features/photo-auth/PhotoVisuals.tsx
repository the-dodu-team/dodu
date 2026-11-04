import type { ManagedPhotoPromise } from './livePhotoTypes'
import { formatPromiseDateTime, formatStartTime } from '../promise/formatPromise'
import clockIcon from '../../assets/final-icons/clock.svg'
import cameraIcon from '../../assets/final-icons/tool-camera.svg'
import purpleClockIcon from '../../assets/final-icons/clock-purple.svg'

export function PhotoSummary({ promise, now, result = false }: { promise: ManagedPhotoPromise; now: number; result?: boolean }) {
  const starts = Date.parse(promise.scheduledAt ?? '')
  const today = Number.isFinite(starts) && new Date(starts + 9 * 3600000).toISOString().slice(0, 10) === new Date(now + 9 * 3600000).toISOString().slice(0, 10)
  const time = Number.isFinite(starts) ? new Date(starts + 9 * 3600000).toISOString().slice(11, 16) : ''
  return <section className="photo-summary final-photo-summary" aria-label={result ? '대상 약속' : '인증할 약속'}>
    <h2>{result ? '대상 약속' : '인증할 약속'}</h2>
    <div className="photo-summary-name"><h3>{promise.taskName}</h3>{today && <span>오늘</span>}</div>
    {Number.isFinite(starts) && <p><img src={clockIcon} alt="" /><span>시작 시간</span><strong>{today ? `오늘 ${formatStartTime(time)}` : formatPromiseDateTime(starts)}</strong></p>}
    <p><img src={cameraIcon} alt="" /><span>인증 도구</span><strong>{promise.tool === 'computer' ? '컴퓨터' : '책'}</strong></p>
  </section>
}

export function PhotoTimeChip({ remaining, ready = false }: { remaining: number; ready?: boolean }) {
  return <p className="photo-time-chip"><img src={purpleClockIcon} alt="" />{ready ? '인증 가능 · ' : ''}남은 시간 {remaining}분</p>
}

// Native vector geometry from the inspected Figma computer illustration.
export function PhotoComputerGuide() {
  return <div className="final-computer-guide" aria-hidden="true">
    <div className="final-computer-frame" /><div className="final-computer-screen" />
    <div className="final-computer-neck" /><div className="final-computer-base" />
  </div>
}
