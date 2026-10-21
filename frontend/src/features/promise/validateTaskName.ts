// 화면 확인용 검사 기준이다.
// 문자 계산·공백 제거·제어문자 세부 범위는 API 연결 전에 FE/BE가 맞춘다.
const FORBIDDEN_TASK_NAME_CHARACTERS = /[\p{Cc}\u2028\u2029]/u

export function hasForbiddenTaskNameCharacters(value: string) {
  return FORBIDDEN_TASK_NAME_CHARACTERS.test(value)
}

export function validateTaskName(value: string) {
  const taskName = value.trim()
  const characterCount = Array.from(taskName).length

  let errorMessage = ''

  // 공백 제거 전에 검사하여 앞뒤의 탭이나 줄바꿈도 거절한다.
  if (hasForbiddenTaskNameCharacters(value)) {
    errorMessage = '작업명에는 줄바꿈이나 제어문자를 넣을 수 없어요.'
  } else if (!taskName) {
    errorMessage =
      value.length > 0
        ? '공백만 입력할 수 없어요.'
        : '작업명을 입력해 주세요.'
  } else if (characterCount > 30) {
    errorMessage = '작업명은 30자 이내로 입력해 주세요.'
  }

  return {
    taskName,
    characterCount,
    errorMessage,
    isValid: errorMessage === '',
  }
}