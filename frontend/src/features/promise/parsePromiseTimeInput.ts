// 모바일 숫자 키보드에서는 콜론 없이 1308처럼 입력해도 된다.
export function parsePromiseTimeInput(value: string) {
  const text = value.trim()
  const match = /^(\d{1,2}):(\d{2})$/.exec(text) ?? /^(\d{2})(\d{2})$/.exec(text)
  if (!match) return null
  const hour = Number(match[1])
  const minute = Number(match[2])
  if (hour > 23 || minute > 59) return null
  return { hour, minute, formatted: `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}` }
}
