import { useCallback, useEffect, useState } from 'react'
import type { createDoduApi } from '../../api/doduApi'
import type { AuthAttempt, Notification, ValidationSurvey } from '../../shared/apiTypes'

type Props = { api: ReturnType<typeof createDoduApi>; promiseId?: string; onBack: () => void }

export default function LiveParticipantPanel({ api, promiseId, onBack }: Props) {
  const [attempts, setAttempts] = useState<AuthAttempt[]>([])
  const [notifications, setNotifications] = useState<Notification[]>([])
  const [survey, setSurvey] = useState<ValidationSurvey | null>(null)
  const [surveySubmitting, setSurveySubmitting] = useState(false)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    try {
      const [nextNotifications, nextAttempts, nextSurvey] = await Promise.all([
        api.listNotifications(),
        promiseId ? api.listAuthAttempts(promiseId) : Promise.resolve([] as AuthAttempt[]),
        promiseId ? api.getValidationSurvey(promiseId) : Promise.resolve(null),
      ])
      setNotifications(nextNotifications)
      setAttempts(nextAttempts)
      setSurvey(nextSurvey)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'PARTICIPANT_DATA_LOAD_FAILED')
    }
  }, [api, promiseId])

  // oxlint-disable-next-line react(set-state-in-effect)
  useEffect(() => { void load() }, [load])

  async function markRead(notification: Notification) {
    if (notification.readAt) return
    const updated = await api.markNotificationRead(notification.notificationId)
    setNotifications(previous => previous.map(item => item.notificationId === updated.notificationId ? updated : item))
  }

  async function skipSurvey() {
    if (!promiseId) return
    setSurvey(await api.skipValidationSurvey(promiseId))
  }

  async function submitSurvey(actualStarted: boolean) {
    if (!promiseId) return
    setSurveySubmitting(true)
    try {
      setSurvey(await api.submitValidationSurvey(promiseId, { actualStarted }))
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'SURVEY_SUBMIT_FAILED')
    } finally {
      setSurveySubmitting(false)
    }
  }

  return <main className="report-page live-participant-panel">
    <header className="report-header"><button type="button" onClick={onBack}>Back</button><h1>History & notifications</h1></header>
    {error && <p role="alert">{error}</p>}
    <section><h2>Authentication history</h2>{attempts.length ? <ul>{attempts.map(item =>
      <li key={item.attemptId}>{item.processingStatus} · {item.decisionResult ?? 'PENDING'} · {item.modelVersion ?? 'model version unavailable'}</li>)}
    </ul> : <p>No authentication attempts.</p>}</section>
    <section><h2>Notifications</h2>{notifications.length ? <ul>{notifications.map(item =>
      <li key={item.notificationId}><button type="button" onClick={() => void markRead(item)}>{item.notificationType} · {item.readAt ? 'Read' : 'Unread'}</button></li>)}
    </ul> : <p>No notifications.</p>}</section>
    {survey && <section><h2>Self-report</h2><p>Status: {survey.lifecycleStatus} / {survey.eligibilityStatus}</p>
      {!survey.submittedAt && survey.lifecycleStatus !== 'SKIPPED' && <>
        <button type="button" disabled={surveySubmitting} onClick={() => void submitSurvey(true)}>Started</button>
        <button type="button" disabled={surveySubmitting} onClick={() => void submitSurvey(false)}>Not started</button>
        <button type="button" onClick={() => void skipSurvey()}>Skip self-report</button>
      </>}
    </section>}
  </main>
}
