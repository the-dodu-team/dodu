import { interventionClosed } from '../home/homeState'
import { useEffect, useRef, useState } from 'react'
import type { Dispatch } from 'react'
import closeIcon from '../../assets/icons/close.svg'
import cameraIcon from '../../assets/icons/camera-filled.svg'
import clockIcon from '../../assets/icons/clock-filled-gray.svg'
import cameraGrayIcon from '../../assets/icons/camera-filled-gray.svg'
import { formatStartTime, getKstDateKey } from '../promise/formatPromise'
import { formatAuthWindow } from '../promise/promiseHistory'
import { canSubmitPhoto } from './photoAuthModel'
import type { MockOutcome, PhotoAction, PhotoSession } from './photoAuthModel'
import './photo-auth.css'

type Props = {
  session: PhotoSession
  dispatch: Dispatch<PhotoAction>
  onClose: () => void
  onRecover: () => void
  recoveryUsed?: boolean
  interventionEndsAt?: number
}

function ToolPicture({ book, photo = false }: { book: boolean; photo?: boolean }) {
  return <svg viewBox="0 0 120 100" className="photo-tool-picture" aria-hidden="true">
    {book ? <>
      <rect x="18" y="15" width="39" height="70" rx="3" fill="#ECEEF5" stroke="#A2A9BE" strokeWidth="3" />
      <rect x="63" y="15" width="39" height="70" rx="3" fill="#ECEEF5" stroke="#A2A9BE" strokeWidth="3" />
      {[29, 41, 53, 65].map(y => <path key={y} d={`M26 ${y}H49M71 ${y}H94`} stroke="#CDD1DE" strokeWidth="2" />)}
    </> : <>
      <rect x="9" y="10" width="102" height="65" rx="8" fill="#FFFFFF" stroke={photo ? '#CDD1DE' : '#3A3E4E'} strokeWidth="4" />
      <rect x="18" y="19" width="84" height="47" rx="2" fill={photo ? '#586BB1' : '#EBE8FF'} />
      <path d="M60 77V92M39 93H81" stroke={photo ? '#CDD1DE' : '#3A3E4E'} strokeWidth="6" strokeLinecap="round" />
    </>}
  </svg>
}

function SummaryCard({ session, result }: { session: PhotoSession; result: boolean }) {
  const promise = session.promise
  const [, month, day] = promise.date.split('-')
  const today = promise.date === getKstDateKey()
  return <section className="photo-summary" aria-label={result ? '대상 약속' : '인증할 약속'}>
    <h2>{result ? '대상 약속' : '인증할 약속'}</h2>
    <div className="photo-summary-name"><h3>{promise.taskName}</h3>{today && <span>오늘</span>}</div>
    <p><img src={clockIcon} width={16} height={16} alt="" /><span>시작 시간</span>
      <strong>{today ? '오늘' : `${Number(month)}월 ${Number(day)}일`} {formatStartTime(promise.startTime)}</strong></p>
    <p><img src={cameraGrayIcon} width={16} height={16} alt="" /><span>인증 도구</span>
      <strong>{promise.tool === 'computer' ? '컴퓨터' : '책'}</strong></p>
  </section>
}

// S08~S09 UI mock이다. 카메라·사진 업로드·판정 API는 호출하지 않는다.
export default function PhotoAuthMockFlow({ session, dispatch, onClose, onRecover, recoveryUsed = false, interventionEndsAt }: Props) {
  const [now, setNow] = useState(Date.now)
  const titleRef = useRef<HTMLHeadingElement>(null)
  const { stage } = session
  const book = session.promise.tool === 'book'
  const result = !['ready', 'preview', 'evaluating'].includes(stage)
  const evaluating = stage === 'evaluating'
  const remaining = Math.max(0, Math.ceil((session.endsAt - now) / 60_000))
  const canAct = canSubmitPhoto(session, now) && !interventionClosed(now, interventionEndsAt)
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(timer)
  }, [])
  useEffect(() => { titleRef.current?.focus() }, [stage])

  const resultCopy = {
    success: {
      eyebrow: `${new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', hour: 'numeric', minute: '2-digit', hour12: true }).format(new Date(session.resolvedAt ?? now))}에 인증되었어요`,
      title: '인증 완료',
      description: '이제 약속한 작업을 시작해 보세요\n결과는 인증 기록에서 확인할 수 있어요',
    },
    expired: {
      // TODO: 마감 후 판정거절의 전용 문구는 PRD·디자인 PNG에 없다. 아래 조합은 임시이며 사용자 설명 후 교체한다.
      eyebrow: session.receivedAt !== null ? '사진이 흐릿하거나 인증 도구가 잘 보이지 않아요'
        : `${new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', hour: 'numeric', minute: '2-digit', hour12: true }).format(new Date(session.endsAt))}까지 제출된 사진이 없어요`,
      title: '인증 가능한 시간이 종료됐어요',
      description: '이번 약속은 "인증 미완료"로 기록돼요.\n다음 약속은 홈에서 새로 만들 수 있어요.',
    },
    rejected: {
      eyebrow: '사진이 흐릿하거나 인증 도구가 잘 보이지 않아요',
      title: '사진을 확인하지 못했어요',
      description: '렌즈를 깨끗하게 닦고,\n인증 도구 전체가 보이게 다시 찍어주세요.',
    },
    'technical-error': {
      eyebrow: '일시적 오류', title: '사진을 전송하지 못했어요',
      description: '인터넷 연결을 확인한 뒤 다시 시도해 주세요\n오류가 계속되면 앱을 닫고 다시 접속해 주세요',
    },
    'tech-failed-recoverable': {
      eyebrow: '시스템 문제로', title: '이번 약속의 인증이 종료되었어요',
      description: '사용자 실패로 기록되지 않아요\n오늘 안에 한 번 다시 약속을 만들 수 있어요',
    },
    'tech-failed-unavailable': {
      eyebrow: '시스템 문제로', title: '이번 약속의 인증이 종료되었어요',
      description: '사용자 실패로 기록되지 않아요\n오늘은 인증 시간(30분)을 마칠 수 있는 시간이 남지 않아\n다시 예약할 수 없어요',
    },
  }
  // TODO: 기술복구 기회 소진 후 재실패의 전용 문구는 미정이다. 설명 축약과 버튼 비활성은 임시 표시다.
  const copy = result ? resultCopy[stage as keyof typeof resultCopy] : null
  const send = (type: 'capture' | 'retake' | 'submit') => {
    const current = Date.now()
    if (interventionClosed(current, interventionEndsAt)) return
    dispatch({ type, now: current })
  }
  const retry = stage === 'rejected' || stage === 'technical-error'

  if (!import.meta.env.DEV) return null
  return <div className="photo-mock-root">
    <details className="photo-mock-controls">
      <summary>개발용 사진 인증 mock · 실제 촬영·전송 없음</summary>
      <label>다음 mock 판정
        <select value={session.outcome} disabled={evaluating}
          onChange={event => dispatch({ type: 'outcome', value: event.target.value as MockOutcome })}>
          <option value="success">인증 완료</option><option value="rejected">사진 거절</option>
          <option value="technical-error">기술오류</option><option value="hold">판정 대기 유지</option>
        </select>
      </label>
      <label><input type="checkbox" checked={session.recoveryAllowed} disabled={recoveryUsed}
        onChange={event => dispatch({ type: 'recovery', allowed: event.target.checked })} />
        서버 복구 가능 응답 가정 (운영 종료시각을 확정하지 않음)
      </label>
      {recoveryUsed && <p>기술복구 1회 사용 완료. 횟수 소진 전용 문구는 디자인에 없어 확정하지 않았습니다.</p>}
      {evaluating && <div className="photo-mock-actions">
        <button onClick={() => dispatch({ type: 'result', outcome: 'success', attempt: session.attempt, now: Date.now() })}>mock 성공 도착</button>
        <button onClick={() => dispatch({ type: 'result', outcome: 'rejected', attempt: session.attempt, now: Date.now() })}>mock 거절 도착</button>
        <button onClick={() => dispatch({ type: 'result', outcome: 'technical-error', attempt: session.attempt, now: Date.now() })}>mock 기술오류 도착</button>
      </div>}
    </details>
    <div className="photo-page">
      <header className="photo-header">
        {!result && <button className="photo-close" onClick={onClose} aria-label="인증 화면 닫기"><img src={closeIcon} width={24} height={24} alt="" /></button>}
        <h1 ref={titleRef} tabIndex={-1}>{result ? '인증 결과' : '인증하기'}</h1>
      </header>
      <main className={`photo-content ${result ? 'photo-result' : stage === 'ready' ? 'photo-ready' : 'photo-review'}`}>
        {stage === 'ready' && <>
          <p className="photo-time-chip"><span aria-hidden="true">◷</span> 인증 가능 · 남은 시간 {remaining}분</p>
          <SummaryCard session={session} result={false} />
          <div className="photo-example"><ToolPicture book={book} />
            <p>{book ? '실제 책이 보이게 찍어주세요' : '컴퓨터 또는 모니터가 보이게 찍어주세요'}</p></div>
        </>}
        {(stage === 'preview' || evaluating) && <>
          <div className="photo-preview-image" role="group" aria-label={`${book ? '책' : '컴퓨터'} 예시 사진 — 개발용 mock`}>
            <ToolPicture book={book} photo />
            {evaluating && <div className="photo-evaluating" role="status">
              <span className="photo-spinner" aria-hidden="true" />
              <strong>사진을 확인하고 있어요</strong><span>보통 몇 초 안에 결과가 나와요</span>
            </div>}
          </div>
          <p className="photo-review-guide">{evaluating ? '결과가 나오면 자동으로 다음 화면으로 넘어가요'
            : `선명하게 ${book ? '책이' : '컴퓨터가'} 잘 보이는지 확인하세요`}</p>
        </>}
        {copy && <>
          <div className={`photo-result-icon ${stage === 'success' ? 'is-success' : ''}`} aria-hidden="true">
            {stage === 'success' ? '✓' : stage === 'expired' ? '◷' : stage === 'rejected'
              ? <img src={cameraIcon} width={44} height={44} alt="" /> : '!'}
          </div>
          <div className="photo-result-heading"><p className={stage === 'success' ? 'photo-success-text' : ''}>{copy.eyebrow}</p><h2>{copy.title}</h2></div>
          <p className="photo-result-description">{recoveryUsed && stage.startsWith('tech-failed-')
            ? '사용자 실패로 기록되지 않아요' : copy.description}</p>
          {retry && <p className="photo-time-chip"><span aria-hidden="true">◷</span> 남은 시간 {remaining}분</p>}
          <SummaryCard session={session} result />
        </>}
      </main>
      <footer className="photo-footer">
        {stage === 'ready' && <>
          <p>{formatAuthWindow(session.startsAt, session.endsAt)} 사이에 인증할 수 있어요</p>
          <button className="photo-primary" disabled={!canAct} onClick={() => send('capture')}><img src={cameraIcon} width={20} height={20} alt="" />카메라 열기</button>
        </>}
        {(stage === 'preview' || evaluating) && <>
          <p>{evaluating ? '판정 중에는 다시 제출할 수 없어요' : `남은 시간 ${remaining}분`}</p>
          <button className="photo-primary" disabled={evaluating || !canAct} onClick={() => send('submit')}>
            {!evaluating && <img src={cameraIcon} width={20} height={20} alt="" />}{evaluating ? '판정 중...' : '제출하기'}</button>
          <button className="photo-secondary" disabled={evaluating || !canAct} onClick={() => send('retake')}>{evaluating ? '다시 찍기' : '다시 촬영하기'}</button>
        </>}
        {retry && <button className="photo-primary" disabled={!canAct} onClick={() => send(stage === 'rejected' ? 'retake' : 'submit')}>
          {stage === 'rejected' && <img src={cameraIcon} width={20} height={20} alt="" />}{stage === 'rejected' ? '다시 찍기' : '다시 시도'}</button>}
        {stage === 'tech-failed-recoverable' && <button className="photo-primary" disabled={recoveryUsed} onClick={onRecover}>약속 생성하기</button>}
        {result && <button className={retry || stage === 'tech-failed-recoverable' ? 'photo-secondary' : 'photo-primary'} onClick={onClose}>홈으로 이동</button>}
      </footer>
    </div>
  </div>
}
