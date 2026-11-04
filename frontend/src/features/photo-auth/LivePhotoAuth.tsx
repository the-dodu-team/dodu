import { useEffect, useRef, useState } from 'react'
import { DoduApiError } from '../../api/doduApi'
import type { createDoduApi } from '../../api/doduApi'
import type { ManagedPhotoPromise } from './livePhotoTypes'
import { compressPhotoForUpload } from './compressPhoto'
import closeIcon from '../../assets/icons/close.svg'
import cameraIcon from '../../assets/icons/camera-filled.svg'
import './photo-auth.css'

type Props = { api: ReturnType<typeof createDoduApi>; promise: ManagedPhotoPromise; onClose: () => void }

export default function LivePhotoAuth({ api, promise, onClose }: Props) {
  const [file, setFile] = useState<File | null>(null)
  const [originalSize, setOriginalSize] = useState(0)
  const [status, setStatus] = useState('CHECKING')
  const [error, setError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [preview, setPreview] = useState('')
  const [refresh, setRefresh] = useState(0)
  const working = useRef(false)
  const requestId = useRef<string | null>(null)
  const picker = useRef<HTMLInputElement>(null)
  const evaluating = status === 'Evaluating'
  const completed = status === 'Completed'
  const locked = isSubmitting || ['CHECKING', 'CHECK_FAILED', 'COMPRESSING', 'Evaluating', 'Completed'].includes(status)
  const toolName = promise.tool === 'computer' ? '컴퓨터' : '책'

  useEffect(() => {
    if (!file) { setPreview(''); return }
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
          setStatus('CHECK_FAILED')
          setError('사진 처리 상태를 확인하지 못했어요. 다시 확인해 주세요.')
          return
        }
        setStatus(state)
        setError('')
        if (state === 'Evaluating') timer = setTimeout(() => { void check() }, 3000)
        else if (latest) requestId.current = null
      } catch {
        if (cancelled) return
        setStatus('CHECK_FAILED')
        setError('사진 처리 상태를 불러오지 못했어요. 다시 확인해 주세요.')
      }
    }
    if (promise.promiseId) void check()
    return () => { cancelled = true; clearTimeout(timer) }
  }, [api, promise.promiseId, refresh])

  async function submit() {
    if (!file || !promise.promiseId || locked || working.current) return
    working.current = true
    setError('')
    setIsSubmitting(true)
    requestId.current ??= crypto.randomUUID()
    try {
      const result = await api.submitPhoto(
        promise.promiseId,
        file,
        promise.tool === 'computer' ? 'COMPUTER' : 'BOOK',
        requestId.current,
      )
      setStatus(result.processingStatus)
      setRefresh(previous => previous + 1)
    } catch (cause) {
      const code = cause instanceof DoduApiError ? cause.response.error.code : ''
      if (code === 'PHOTO_EVALUATION_IN_PROGRESS') {
        setStatus('Evaluating')
        setRefresh(previous => previous + 1)
      } else {
        setStatus('CHECK_FAILED')
        setError(code === 'PHOTO_ALREADY_USED_ON_ANOTHER_DAY'
          ? '다른 날 사용한 사진이에요. 지금 작업도구를 다시 촬영해 주세요.'
          : '사진 제출 결과를 확인하지 못했어요. 처리 상태를 먼저 확인해 주세요.')
      }
    } finally {
      setIsSubmitting(false)
      working.current = false
    }
  }

  async function selectPhoto(selected: File | undefined) {
    if (!selected || locked || working.current) return
    working.current = true
    requestId.current = null
    setFile(null)
    setError('')
    setStatus('COMPRESSING')
    try {
      const compressed = await compressPhotoForUpload(selected)
      setFile(compressed)
      setOriginalSize(selected.size)
      setStatus('READY')
    } catch {
      setFile(null)
      setOriginalSize(0)
      setStatus('READY')
      setError('사진을 준비하지 못했어요. JPG 또는 PNG 사진을 다시 선택해 주세요.')
    } finally { working.current = false }
  }

  return <main className="photo-page live-photo-page">
    <header className="photo-header"><button className="photo-close" type="button" aria-label="닫기" onClick={onClose}>
      <img src={closeIcon} width={20} height={20} alt="" />
    </button><h1>인증하기</h1></header>
    <div className="photo-content live-photo-content">
      {!preview && <div className="photo-summary"><h2>인증할 약속</h2>
        <div className="photo-summary-name"><h3>{promise.taskName}</h3></div>
        <p>{toolName}가 선명하게 보이도록 사진을 촬영해 주세요.</p></div>}
      {preview && <><div className="photo-preview-image">
        <img className="live-photo-preview" src={preview} alt="제출할 작업도구 사진" />
        {evaluating && <div className="photo-evaluating"><span className="photo-spinner" aria-hidden="true" />
          <strong>사진을 확인하고 있어요</strong><span>결과가 나올 때까지 잠시 기다려 주세요</span></div>}
      </div><p className="photo-review-guide">선명하게 {toolName}가 잘 보이는지 확인하세요</p>
        <p className="live-photo-size">사진 크기 {sizeLabel(originalSize)} → {sizeLabel(file!.size)}</p></>}
      <input ref={picker} className="visually-hidden" aria-label="작업도구 사진 선택" type="file"
        accept="image/jpeg,image/png" capture="environment" disabled={locked}
        onChange={event => { void selectPhoto(event.target.files?.[0]); event.target.value = '' }} />
      <p className="photo-review-guide" role="status">{completed ? '사진 인증이 완료되었어요'
        : evaluating ? '사진을 확인하고 있어요'
          : status === 'Rejected' ? '작업도구를 확인하기 어려워요. 인증 시간 안에 다시 촬영해 주세요.'
            : status === 'TechFailed' ? '시스템 문제로 판정을 완료하지 못했어요. 사용자 실패로 기록되지 않아요.'
              : status === 'CHECKING' ? '처리 상태를 확인하고 있어요'
                : status === 'COMPRESSING' ? '사진을 준비하고 있어요' : ''}</p>
      {error && <p role="alert">{error}</p>}
      {status === 'CHECK_FAILED' && <button className="photo-secondary" onClick={() => {
        setStatus('CHECKING'); setRefresh(previous => previous + 1)
      }}>처리 상태 다시 확인</button>}
    </div>
    <footer className="photo-footer">
      {completed ? <button className="photo-primary" onClick={onClose}>홈으로 돌아가기</button>
        : <button className="photo-primary" disabled={locked} onClick={() => file ? void submit() : picker.current?.click()}>
          <img src={cameraIcon} width={20} height={20} alt="" />
          {evaluating ? '판정 중이에요' : isSubmitting ? '제출 중이에요' : file ? '제출하기' : '사진 촬영하기'}
        </button>}
      {file && !locked && <button className="photo-secondary" onClick={() => picker.current?.click()}>다시 촬영하기</button>}
    </footer>
  </main>
}

function sizeLabel(bytes: number) {
  return bytes < 1024 * 1024 ? `${Math.max(1, Math.ceil(bytes / 1024))}KB` : `${(bytes / 1024 / 1024).toFixed(1)}MB`
}
