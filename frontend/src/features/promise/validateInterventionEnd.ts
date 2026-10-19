const AUTH_WINDOW_MS = 30 * 60_000

// D03: 시작 시각부터 인증 시간 30분이 공식 종료시각 안에 모두 포함되어야 한다.
// 실제 종료시각은 운영 데이터로 받으며, 이 파일에 운영 날짜를 고정하지 않는다.
export function getInterventionEndError(
  scheduledAt: number,
  interventionEndsAt?: number,
) {
  if (interventionEndsAt === undefined) return ''
  if (!Number.isFinite(interventionEndsAt) || scheduledAt + AUTH_WINDOW_MS > interventionEndsAt) {
    return '행동검증 종료시간 30분 전부터는 약속을 생성할 수 없어요'
  }
  return ''
}
