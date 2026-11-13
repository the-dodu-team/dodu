type InitialTimeOptions = {
  initialValue: string
  now: number
  isEditing: boolean
  isBlocked: (date: string) => boolean
  interventionEndsAt?: number
}

// 사용 가능한 날짜를 먼저 고르되 기존 수정값과 뒤로 가기 선택값은 보존한다.
export function chooseInitialPromiseTime({ initialValue, now, isEditing, isBlocked, interventionEndsAt }: InitialTimeOptions) {
  const minute = 60_000
  const day = 24 * 60 * minute
  const dateKey = (time: number) => new Date(time + 9 * 60 * minute).toISOString().slice(0, 10)
  if (isEditing) return initialValue
  const previous = Date.parse(initialValue)
  const first = Number.isFinite(previous) ? previous : Math.ceil((now + 5 * minute) / (5 * minute)) * 5 * minute
  if (!isBlocked(dateKey(first))) return initialValue
  for (let offset = 1; offset <= 3; offset++) {
    const desired = first + offset * day
    const dayStart = Date.parse(dateKey(desired) + 'T00:00:00+09:00')
    const latest = Math.min(now + 72 * 60 * minute, interventionEndsAt === undefined ? Infinity : interventionEndsAt - 30 * minute)
    const candidate = Math.floor(Math.min(desired, latest) / (5 * minute)) * 5 * minute
    if (candidate < dayStart || candidate < now + 5 * minute) break
    if (!isBlocked(dateKey(candidate))) return new Date(candidate).toISOString()
  }
  // 가능한 날짜가 없으면 막힌 날짜를 유지하고 안내를 확인하게 한다.
  return initialValue
}
