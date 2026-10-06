import type { PromiseDraft, PreviewPromise } from './types'

// 미리보기 전용이다. 서버 인증 결과를 수정하거나 실제 예약을 저장하지 않는다.
export function reconfirmPromise(
  previous: PreviewPromise | null,
  draft: PromiseDraft,
  date: string,
): PreviewPromise {
  const taskName = draft.taskName.trim()
  const unchanged = previous?.date === date &&
    previous.taskName === taskName &&
    previous.startTime === draft.startTime &&
    previous.tool === draft.tool

  return { ...draft, taskName, date, status: unchanged ? previous.status : 'pending' }
}
