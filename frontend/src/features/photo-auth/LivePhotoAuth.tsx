import { useEffect, useRef, useState } from 'react'
import { DoduApiError } from '../../api/doduApi'
import type { createDoduApi } from '../../api/doduApi'
import type { ManagedPhotoPromise } from './livePhotoTypes'
import { compressPhotoForUpload } from './compressPhoto'
import { photoPresentation, photoCanSubmit } from './livePhotoPresentation'
import { PhotoSummary, PhotoTimeChip, PhotoComputerGuide } from './PhotoVisuals'
import { ToolPicture } from './PhotoAuthMockFlow'
import ScreenHeader from '../../components/ScreenHeader'
import { notify } from '../../components/feedback'
import { formatAuthWindow } from '../promise/promiseHistory'
import cameraIcon from '../../assets/final-icons/camera.svg'
import checkIcon from '../../assets/final-icons/check.svg'
import rejectIcon from '../../assets/final-icons/camera-red.svg'
import alertIcon from '../../assets/final-icons/alert.svg'
import endedIcon from '../../assets/final-icons/clock-red.svg'
import './photo-auth.css'

type Props = { api: ReturnType<typeof createDoduApi>; promise: ManagedPhotoPromise; onClose: () => void; getNow?: () => number }

export default function LivePhotoAuth({ api, promise, onClose, getNow = Date.now }: Props) {
  const [file, setFile] = useState<File | null>(null)
  const [status, setStatus] = useState('CHECKING')
  const [error, setError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [preview, setPreview] = useState('')
  const [refresh, setRefresh] = useState(0)
  const [decidedAt, setDecidedAt] = useState<string | null>(null)
  const [now, setNow] = useState(getNow)
  const clock = useRef(getNow)
  const working = useRef(false)
  const requestId = useRef<string | null>(null)
  const picker = useRef<HTMLInputElement>(null)
  const startsAt = Date.parse(promise.scheduledAt ?? '')
  const endsAt = startsAt + 30 * 60000
  const remaining = Math.max(0, Math.ceil((endsAt - now) / 60000))
  const stage = photoPresentation(status, Boolean(file), now, startsAt)
  const result = ['success', 'rejected', 'technical', 'technical-final', 'ended'].includes(stage)
  const canAct = photoCanSubmit(stage, now, startsAt) && !isSubmitting
  const locked = !canAct
  const toolName = promise.tool === 'computer' ? '컴퓨터' : '책'

  useEffect(() => { clock.current = getNow }, [getNow])
  useEffect(() => {
    const timer = setInterval(() => setNow(clock.current()), 1000)
    return () => clearInterval(timer)
  }, [])
  useEffect(() => {
    if (!file) return
    const url = URL.createObjectURL(file)
    setPreview(url)
    return () => URL.revokeObjectURL(url)
  }, [file])

  useEffect(() => {
    let cancelled = false
    let timer: ReturnType<typeof setTimeout> | undefined
    async function check() {
      try {
        const attempts = await api.listAuthAttempts(promise.promiseId!)
        if (cancelled) return
        const latest = attempts[0]
        const state = latest?.processingStatus ?? 'READY'
        if (!['READY', 'Evaluating', 'Completed', 'Rejected', 'TechFailed'].includes(state)) {
          setStatus('CHECK_FAILED'); setError('사진 처리 상태를 확인하지 못했어요. 다시 확인해 주세요.'); return
        }
        setStatus(state); setDecidedAt(latest?.decidedAt ?? null); setError('')
        if (state === 'Evaluating') timer = setTimeout(() => { void check() }, 3000)
        else if (latest) requestId.current = null
      } catch {
        if (!cancelled) { setStatus('CHECK_FAILED'); setError('사진 처리 상태를 불러오지 못했어요. 다시 확인해 주세요.') }
      }
    }
    if (promise.promiseId) void check()
    return () => { cancelled = true; clearTimeout(timer) }
  }, [api, promise.promiseId, refresh])

  async function submit() {
    if (!file || !promise.promiseId || working.current || !photoCanSubmit(stage, clock.current(), startsAt)) return
    working.current = true; setError(''); setIsSubmitting(true)
    requestId.current ??= crypto.randomUUID()
    try {
      const response = await api.submitPhoto(promise.promiseId, file,
        promise.tool === 'computer' ? 'COMPUTER' : 'BOOK', requestId.current)
      notify('success', '사진이 접수됐어요. 인증 결과를 확인하고 있어요.')
      setStatus(response.processingStatus); setRefresh(previous => previous + 1)
    } catch (cause) {
      const code = cause instanceof DoduApiError ? cause.response.error.code : ''
      if (code === 'PHOTO_EVALUATION_IN_PROGRESS') { setStatus('Evaluating'); setRefresh(previous => previous + 1) }
      else if (code === 'PHOTO_WINDOW_CLOSED') { setStatus('WINDOW_CLOSED'); notify('error', '사진을 제출할 수 있는 시간이 지났어요.') }
      else {
        setStatus('CHECK_FAILED')
        if (code === 'PHOTO_ALREADY_USED_ON_ANOTHER_DAY') { setFile(null); setPreview(''); requestId.current = null }
        setError(code === 'PHOTO_ALREADY_USED_ON_ANOTHER_DAY'
          ? '다른 날 사용한 사진이에요. 지금 작업도구를 다시 촬영해 주세요.'
          : '사진 제출 결과를 확인하지 못했어요. 처리 상태를 먼저 확인해 주세요.')
        notify('error', code === 'PHOTO_ALREADY_USED_ON_ANOTHER_DAY' ? '다른 날 사용한 사진이에요. 다시 촬영해 주세요.' : '사진 제출 결과를 확인하지 못했어요. 처리 상태를 먼저 확인해 주세요.')
      }
    } finally { setIsSubmitting(false); working.current = false }
  }
  async function selectPhoto(selected: File | undefined) {
    if (!selected || locked || working.current) return
    working.current = true; requestId.current = null; setFile(null); setPreview(''); setError(''); setStatus('COMPRESSING')
    try { setFile(await compressPhotoForUpload(selected)); setStatus('READY') }
    catch { setStatus('READY'); setError('사진을 준비하지 못했어요. JPG 또는 PNG 사진을 다시 선택해 주세요.'); notify('error', '사진을 준비하지 못했어요. JPG 또는 PNG 사진을 다시 선택해 주세요.') }
    finally { working.current = false }
  }
  function retryRead() { setStatus('CHECKING'); setRefresh(previous => previous + 1) }
  const successTime = decidedAt && Number.isFinite(Date.parse(decidedAt))
    ? new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', hour: 'numeric', minute: '2-digit' }).format(new Date(decidedAt)) + '에 인증되었어요'
    : '사진 인증이 완료되었어요'
  const copies = {
    success: { icon: checkIcon, eyebrow: successTime, title: '인증 완료', description: '이제 약속한 작업을 시작해 보세요\n결과는 인증 기록에서 확인할 수 있어요' },
    rejected: { icon: rejectIcon, eyebrow: '인증 도구가 잘 보이는지 확인해 주세요', title: '사진을 확인하지 못했어요', description: '렌즈를 깨끗하게 닦고,\n인증 도구 전체가 보이게 다시 찍어주세요.' },
    technical: { icon: alertIcon, eyebrow: '일시적 오류', title: '사진 상태를 확인하지 못했어요', description: '인터넷 연결을 확인한 뒤 다시 시도해 주세요\n오류가 계속되면 앱을 닫고 다시 접속해 주세요' },
    'technical-final': { icon: alertIcon, eyebrow: '시스템 문제', title: '인증을 마치지 못했어요', description: '사용자 실패로 기록되지 않아요.\n홈에서 다음 약속을 확인해 주세요.' },
    ended: { icon: endedIcon, eyebrow: '인증 가능 시간이 지났어요', title: '인증 가능한 시간이 종료됐어요', description: '이번 약속은 “인증 미완료”로 기록돼요.\n다음 약속은 홈에서 새로 만들 수 있어요.' },
  }
  const copy = result ? copies[stage as keyof typeof copies] : null

  return <main className="photo-page live-photo-page final-photo-page">
    <ScreenHeader title={result ? '인증 결과' : '인증하기'} close={!result} onBack={result ? undefined : onClose} />
    {copy ? <div className="photo-content photo-result final-photo-result" aria-live="polite">
      <div className={`photo-result-icon ${stage === 'success' ? 'is-success' : ''}`}><img src={copy.icon} alt="" /></div>
      <div className="photo-result-heading"><p className={stage === 'success' ? 'photo-success-text' : ''}>{copy.eyebrow}</p><h2>{copy.title}</h2></div>
      <p className="photo-result-description">{copy.description}</p>
      {stage === 'rejected' && Number.isFinite(endsAt) && <PhotoTimeChip remaining={remaining} />}
      {error && <p className="final-photo-error" role="alert">{error}</p>}
      <PhotoSummary promise={promise} now={now} result />
    </div> : <div className={`photo-content ${stage === 'ready' ? 'final-photo-ready' : 'final-photo-review'}`}>
      {stage === 'ready' && <>
        {Number.isFinite(endsAt) && <PhotoTimeChip remaining={remaining} ready />}
        <PhotoSummary promise={promise} now={now} />
        <div className="photo-example">{promise.tool === 'computer' ? <PhotoComputerGuide /> : <ToolPicture book />}
          <p>{promise.tool === 'computer' ? '컴퓨터 또는 모니터가 보이게 찍어주세요' : '실제 책이 보이게 찍어주세요'}</p></div>
      </>}
      {(stage === 'review' || stage === 'evaluating') && <>
        <div className="photo-preview-image">
          {file && preview && <img className="live-photo-preview" src={preview} alt="제출할 작업도구 사진" />}
          {stage === 'evaluating' && <div className="photo-evaluating" role="status"><span className="photo-spinner" aria-hidden="true" />
            <strong>사진을 확인하고 있어요</strong><span>결과가 나올 때까지 잠시 기다려 주세요</span></div>}
        </div><p className="photo-review-guide">선명하게 {toolName}{promise.tool === 'book' ? '이' : '가'} 잘 보이는지 확인하세요</p>
      </>}
      {stage === 'loading' && <div className="screen-state-content" role="status"><span className="screen-spinner" aria-hidden="true" />
        <p>{status === 'COMPRESSING' ? '사진을 준비하고 있어요' : '사진 처리 상태를 확인하고 있어요'}</p></div>}
      {error && <p className="final-photo-error" role="alert">{error}</p>}
    </div>}
    <input ref={picker} className="visually-hidden" aria-label="작업도구 사진 선택" type="file" accept="image/jpeg,image/png" capture="environment"
      disabled={locked} onChange={event => { void selectPhoto(event.target.files?.[0]); event.target.value = '' }} />
    <footer className="photo-footer">
      {!result && stage !== 'loading' && Number.isFinite(startsAt) && <p>{stage === 'ready'
        ? `${formatAuthWindow(startsAt, endsAt)} 사이에 인증할 수 있어요`
        : stage === 'evaluating' && remaining === 0 ? '마감 전에 접수한 사진의 판정 결과를 기다리고 있어요' : `남은 시간 ${remaining}분`}</p>}
      {stage === 'technical' ? <><button className="photo-primary" onClick={retryRead}>처리 상태 다시 확인</button><button className="photo-secondary" onClick={onClose}>홈으로 이동</button></>
        : ['success', 'ended', 'technical-final'].includes(stage) ? <button className="photo-primary" onClick={onClose}>홈으로 이동</button>
          : stage === 'rejected' ? <><button className="photo-primary" disabled={!canAct} onClick={() => picker.current?.click()}><img src={cameraIcon} alt="" />다시 찍기</button><button className="photo-secondary" onClick={onClose}>홈으로 이동</button></>
            : <><button className="photo-primary" disabled={locked} onClick={() => file ? void submit() : picker.current?.click()}>
              {stage !== 'evaluating' && <img src={cameraIcon} alt="" />}{stage === 'evaluating' ? '판정 중이에요' : isSubmitting ? '제출 중이에요' : file ? '제출하기' : '카메라 열기'}
            </button>{file && stage === 'review' && <button className="photo-secondary" disabled={!canAct} onClick={() => picker.current?.click()}>다시 촬영하기</button>}</>}
    </footer>
  </main>
}
