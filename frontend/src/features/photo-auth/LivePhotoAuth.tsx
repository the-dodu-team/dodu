import { useState } from 'react'
import type { createDoduApi } from '../../api/doduApi'
import type { ManagedPhotoPromise } from './livePhotoTypes'

type Props = { api: ReturnType<typeof createDoduApi>; promise: ManagedPhotoPromise; onClose: () => void }

export default function LivePhotoAuth({ api, promise, onClose }: Props) {
  const [file, setFile] = useState<File | null>(null)
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

  return <main className="photo-page live-photo-page">
    <header className="photo-header"><button type="button" onClick={onClose}>Back</button><h1>Photo verification</h1></header>
    <section className="photo-content">
      <h2>{promise.taskName}</h2>
      <p>Upload one JPG or PNG image. The server and AI worker decide the result.</p>
      <input type="file" accept="image/jpeg,image/png" onChange={event => setFile(event.target.files?.[0] ?? null)} />
      <p role="status">Status: {status}</p>
      {error && <p role="alert">{error}</p>}
    </section>
    <footer className="photo-footer"><button className="photo-primary" disabled={!file || isSubmitting} onClick={submit}>
      {isSubmitting ? 'Submitting…' : 'Submit photo'}
    </button></footer>
  </main>
}
