import { ParticipantPromiseApiError } from '../../api/participantPromiseApi'
import type { PromisePayload } from '../../api/participantPromiseApi'

const messages: Record<string, string> = {
  ACCESS_CODE_INVALID: '코드를 다시 확인해 주세요. 유효하지 않거나 만료된 코드예요.',
  ACCESS_REQUIRED: '참가자 코드를 입력해 다시 입장해 주세요.',
  ACCESS_REVOKED: '서비스 접근이 종료되었어요. 확인이 필요하면 운영자에게 문의해 주세요.',
  SESSION_REPLACED: '다른 기기에서 입장했어요. 이 기기에서 사용하려면 다시 입장해 주세요.',
  PROMISE_ACTIVE_EXISTS: '진행 중인 약속이 있어요. 홈에서 기존 약속을 확인해 주세요.',
  PROMISE_DATE_ALREADY_USED: '이미 약속을 만든 날짜예요. 다른 날짜를 선택해 주세요.',
  PROMISE_NOT_MODIFIABLE: '시작 시간이 지나 수정·취소할 수 없어요.',
  PROMISE_RESCHEDULE_LIMIT_REACHED: '시작 시간을 이미 1번 수정했어요.',
  PROMISE_SCHEDULE_TOO_SOON: '저장 시점부터 최소 5분 뒤로 시작 시간을 다시 선택해 주세요.',
  PROMISE_SCHEDULE_TOO_FAR: '지금부터 72시간 이내로 정해 주세요.',
  PROMISE_SCHEDULE_AFTER_STUDY_END: '참여 기간 안에 인증을 마칠 수 있는 시간을 선택해 주세요.',
  PROMISE_VERSION_CONFLICT: '다른 화면에서 약속이 변경됐어요. 최신 약속을 확인한 뒤 다시 시도해 주세요.',
  PROMISE_CREATION_IN_PROGRESS: '이전에 보낸 약속을 처리 중이에요. 저장 결과를 먼저 확인해 주세요.',
  IDEMPOTENCY_KEY_REUSED: '저장 요청 정보가 변경됐어요. 홈으로 돌아가 약속을 다시 만들어 주세요.',
  REQUEST_IN_PROGRESS: '이전 저장 결과를 먼저 확인해 주세요. 확인 전에는 다른 약속을 저장할 수 없어요.',
  STUDY_PERIOD_ENDED: '약속을 만들 수 있는 참여 기간이 종료되었어요.',
  INVALID_TASK_TITLE: '작업명을 1자 이상 30자 이내로 입력해 주세요.',
  INVALID_TOOL_TYPE: '인증 도구를 다시 선택해 주세요.',
  DATETIME_OFFSET_REQUIRED: '시작 날짜와 시간을 다시 선택해 주세요.',
  POLICY_UNAVAILABLE: '운영 설정을 확인 중이에요. 잠시 후 다시 시도해 주세요.',
}

export function promiseErrorMessage(error: unknown): string {
  if (error instanceof ParticipantPromiseApiError) {
    if (messages[error.code]) return messages[error.code]
    if (error.httpStatus === 409) return '현재 약속 상태에서는 저장할 수 없어요. 홈에서 약속을 확인해 주세요.'
    if (error.httpStatus === 429) return '요청이 많아 잠시 기다려야 해요. 잠시 후 다시 시도해 주세요.'
    if (error.httpStatus === 403) return '이 약속에 접근할 수 없어요. 참가자 정보를 확인해 주세요.'
  }
  return '연결 상태를 확인한 뒤 다시 시도해 주세요. 저장 결과가 불확실하면 서버 상태를 먼저 확인해 주세요.'
}

export function scheduledDate(payload: Pick<PromisePayload, 'scheduledAt'>): string {
  const timestamp = Date.parse(payload.scheduledAt)
  return Number.isFinite(timestamp) ? new Date(timestamp + 9 * 3600000).toISOString().slice(0, 10) : ''
}

export function creationBlockCode(payload: PromisePayload, now: number, active: boolean, blockedDates: Set<string>): string | null {
  if (active) return 'PROMISE_ACTIVE_EXISTS'
  const starts = Date.parse(payload.scheduledAt)
  if (!Number.isFinite(starts)) return 'DATETIME_OFFSET_REQUIRED'
  // A finished promise releases the single active slot, including on the same KST date.
  void blockedDates
  if (starts < now + 5 * 60000) return 'PROMISE_SCHEDULE_TOO_SOON'
  if (starts > now + 72 * 3600000) return 'PROMISE_SCHEDULE_TOO_FAR'
  return null
}
