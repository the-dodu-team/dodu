export function getKstDateKey() {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(new Date())

  const year = parts.find((part) => part.type === 'year')!.value
  const month = parts.find((part) => part.type === 'month')!.value
  const day = parts.find((part) => part.type === 'day')!.value

  return `${year}-${month}-${day}`
}

export function formatStartTime(startTime: string) {
  const [hours, minutes] = startTime.split(':')
  const hour = Number(hours)
  const period = hour < 12 ? '오전' : '오후'

  return `${period} ${hour % 12 || 12}:${minutes}`
}

// 숫자 날짜는 브라우저 로캘의 마침표 대신 슬래시로 표시한다.
export function formatPromiseDateTime(timestamp: number) {
  const kst = new Date(timestamp + 9 * 60 * 60_000)
  if (!Number.isFinite(timestamp)) return ''
  const time = String(kst.getUTCHours()).padStart(2, '0') + ':' + String(kst.getUTCMinutes()).padStart(2, '0')
  return kst.getUTCMonth() + 1 + '/' + kst.getUTCDate() + ' ' + formatStartTime(time)
}
