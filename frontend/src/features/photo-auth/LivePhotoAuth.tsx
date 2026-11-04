import { useState } from 'react'
import type { createDoduApi } from '../../api/doduApi'
import type { ManagedPhotoPromise } from './livePhotoTypes'
import { compressPhotoForUpload } from './compressPhoto'

type Props = { api: ReturnType<typeof createDoduApi>; promise: ManagedPhotoPromise; onClose: () => void }

export default function LivePhotoAuth({ api, promise, onClose }: Props) {
  const [file, setFile] = useState<File | null>(null)
  const [originalSize, setOriginalSize] = useState(0)
  const [status, setStatus] = useState('READY')
  const [error, setError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  async function submit() {
    if (!file || !promise.promiseId) return
    setError('')
    setIsSubmitting(true)
    try {
      const result = await api.submitPhoto(
        promise.promiseId,
        file,
        promise.tool === 'computer' ? 'COMPUTER' : 'BOOK',
      )
      setStatus(result.processingStatus)
    } catch (cause) {
      setStatus('TECHNICAL_ERROR')
      setError(cause instanceof Error ? cause.message : 'PHOTO_SUBMIT_FAILED')
    } finally {
      setIsSubmitting(false)
    }
  }

  async function selectPhoto(selected: File | undefined) {
    if (!selected) return
    setError('')
    setStatus('COMPRESSING')
    try {
      const compressed = await compressPhotoForUpload(selected)
      setFile(compressed)
      setOriginalSize(selected.size)
      setStatus('READY')
    } catch (cause) {
      setFile(null)
      setOriginalSize(0)
      setStatus('TECHNICAL_ERROR')
      setError(cause instanceof Error ? cause.message : 'PHOTO_COMPRESSION_FAILED')
    }
  }

  return <main className="photo-page live-photo-page">
    <header className="photo-header"><button type="button" onClick={onClose}>Back</button><h1>Photo verification</h1></header>
    <section className="photo-content">
      <h2>{promise.taskName}</h2>
      <p>Upload one JPG or PNG image. It is resized and compressed in your browser before upload.</p>
      <input type="file" accept="image/jpeg,image/png" onChange={event => { void selectPhoto(event.target.files?.[0]) }} />
      {file && <p>Compressed: {(originalSize / 1024 / 1024).toFixed(1)}MB → {(file.size / 1024 / 1024).toFixed(1)}MB</p>}
      <p role="status">Status: {status}</p>
      {error && <p role="alert">{error}</p>}
    </section>
    <footer className="photo-footer"><button className="photo-primary" disabled={!file || isSubmitting} onClick={submit}>
      {isSubmitting ? 'Submitting…' : 'Submit photo'}
    </button></footer>
  </main>
}
