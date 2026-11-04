export type LivePhotoStage = 'loading' | 'ready' | 'review' | 'evaluating' | 'success' | 'rejected' | 'technical' | 'technical-final' | 'ended'

export function photoPresentation(status: string, hasPhoto: boolean, now: number, startsAt: number): LivePhotoStage {
  // A valid pre-deadline submission may still be evaluating after the deadline.
  if (status === 'Completed') return 'success'
  if (status === 'Evaluating') return 'evaluating'
  if (status === 'TechFailed') return 'technical-final'
  if (status === 'CHECK_FAILED') return 'technical'
  if (['CHECKING', 'COMPRESSING'].includes(status)) return 'loading'
  if (!Number.isFinite(startsAt) || !Number.isFinite(now)) return 'technical'
  if (status === 'WINDOW_CLOSED' || (Number.isFinite(startsAt) && now >= startsAt + 30 * 60000)) return 'ended'
  if (status === 'Rejected') return 'rejected'
  return hasPhoto ? 'review' : 'ready'
}

export function photoCanSubmit(stage: LivePhotoStage, now: number, startsAt: number): boolean {
  return ['ready', 'review', 'rejected'].includes(stage) &&
    Number.isFinite(startsAt) && now >= startsAt && now < startsAt + 30 * 60000
}
