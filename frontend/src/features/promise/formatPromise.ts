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
