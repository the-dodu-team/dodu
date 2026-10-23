import type { PreviewPromise } from './types'

export type FinalPromiseRecord = {
  date: string
  status: 'verified' | 'unverified'
}

// pending은 아직 사진을 제출하지 않은 현재 mock의 상태다.
// 실제 API 연결 후에는 서버가 확정한 결과만 사용한다.
export function getFinalPromiseRecord(
  promise: PreviewPromise | null,
  now: number,
): FinalPromiseRecord | null {
  if (!promise) return null
  if (promise.status === 'verified' || promise.status === 'unverified') {
    return { date: promise.date, status: promise.status }
  }
  // 판정 대기·기술 오류·권한 문제 등을 시간 경과만으로 미완료로 바꾸지 않는다.
  if (promise.status !== 'pending') return null
  const start = Date.parse(`${promise.date}T${promise.startTime}:00+09:00`)
  if (!Number.isFinite(start) || now < start + 30 * 60_000) return null
  return { date: promise.date, status: 'unverified' }
}

export function getWeekRange(today: string) {
  const monday = new Date(`${today}T00:00:00Z`)
  monday.setUTCDate(monday.getUTCDate() - (monday.getUTCDay() + 6) % 7)
  const sunday = new Date(monday)
  sunday.setUTCDate(sunday.getUTCDate() + 6)
  return { monday, sunday }
}

export function getWeeklyFinalRecords(records: FinalPromiseRecord[], today: string) {
  const { monday, sunday } = getWeekRange(today)
  const start = monday.toISOString().slice(0, 10)
  const end = sunday.toISOString().slice(0, 10)
  return records.filter(record =>
    (record.status === 'verified' || record.status === 'unverified') &&
    record.date >= start && record.date <= end,
  )
}

export function formatAuthWindow(start: number, end: number) {
  const date = (value: number) => new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'Asia/Seoul', month: 'long', day: 'numeric',
  }).format(new Date(value))
  const time = (value: number) => {
    const kst = new Date(value + 9 * 60 * 60_000)
    const hour = kst.getUTCHours()
    const minute = String(kst.getUTCMinutes()).padStart(2, '0')
    return `${hour < 12 ? '오전' : '오후'} ${hour % 12 || 12}:${minute}`
  }
  const key = (value: number) => new Date(value + 9 * 60 * 60_000).toISOString().slice(0, 10)
  return `${date(start)} ${time(start)} ~ ${key(start) === key(end) ? '' : `${date(end)} `}${time(end)}`
}
