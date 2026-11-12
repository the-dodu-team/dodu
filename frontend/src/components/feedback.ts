export type FeedbackKind = 'success' | 'error'
export const feedbackEvent = 'dodu:feedback'

// 서버 응답·예외 원문 대신 호출부에서 정한 사용자 문구만 전달한다.
export function notify(kind: FeedbackKind, message: string) {
  window.dispatchEvent(new CustomEvent(feedbackEvent, { detail: { kind, message } }))
}
