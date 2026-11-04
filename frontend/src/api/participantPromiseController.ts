import { createParticipantPromiseApi, ParticipantPromiseApiError } from './participantPromiseApi'
import type { ApiResult, ParticipantState, PromisePayload, PromiseResponse } from './participantPromiseApi'

export type RestoredParticipantPromise = {
  participant: ApiResult<ParticipantState>
  activePromise: ApiResult<PromiseResponse | null>
}

// UI와 토큰 저장 방식은 이 모듈에서 확정하지 않는다.
// 상태 필드가 없는 약속 응답을 Scheduled나 AuthCompleted로 추측하지 않는다.
export function createParticipantPromiseController(options: {
  getToken: () => string | null
  fetcher?: typeof fetch
}) {
  const api = createParticipantPromiseApi(options)
  let revision = 0
  let reader: AbortController | null = null
  let writing = false
  let unresolved: 'create' | 'reschedule' | 'cancel' | null = null
  let prepared: ReturnType<typeof api.prepareCreate> | null = null
  let preparedBody: string | null = null

  function invalidateReads() {
    revision += 1
    reader?.abort()
    reader = null
  }

  async function write<T>(kind: 'create' | 'reschedule' | 'cancel', action: () => Promise<T>) {
    if (writing || (unresolved && !(kind === 'create' && unresolved === 'create'))) {
      throw new ParticipantPromiseApiError('REQUEST_IN_PROGRESS', 409)
    }
    writing = true
    invalidateReads()
    try {
      const result = await action()
      unresolved = null
      if (kind !== 'create') {
        prepared = null
        preparedBody = null
      }
      return result
    } catch (error) {
      if (error instanceof ParticipantPromiseApiError && error.outcomeUnknown) unresolved = kind
      throw error
    } finally {
      writing = false
      invalidateReads()
    }
  }

  return {
    // 입장은 기존 기기의 세션을 종료할 수 있으므로 호출자가 사용자 동작에 연결한다.
    enter: api.enter,

    async restore(): Promise<RestoredParticipantPromise> {
      if (writing) throw new ParticipantPromiseApiError('REQUEST_IN_PROGRESS', 409)
      invalidateReads()
      const current = revision
      const token = options.getToken()
      const request = new AbortController()
      reader = request
      try {
        const participant = await api.me(request.signal)
        const activePromise = await api.active(request.signal)
        if (current !== revision || token !== options.getToken()) {
          throw new DOMException('Superseded request', 'AbortError')
        }
        return { participant, activePromise }
      } finally {
        if (reader === request) reader = null
      }
    },

    create(payload: PromisePayload) {
      // 같은 입력의 재시도는 최초 요청의 멱등키를 유지한다.
      const body = JSON.stringify({
        taskTitle: payload.taskTitle, toolType: payload.toolType, scheduledAt: payload.scheduledAt,
      })
      if (writing || (unresolved && body !== preparedBody)) {
        return Promise.reject(new ParticipantPromiseApiError('REQUEST_IN_PROGRESS', 409))
      }
      if (body !== preparedBody || !prepared) {
        preparedBody = body
        prepared = api.prepareCreate(payload)
      }
      const submission = prepared
      return write('create', () => submission.send())
    },

    reschedule(id: string, scheduledAt: string, expectedVersion: number) {
      return write('reschedule', () => api.reschedule(id, scheduledAt, expectedVersion))
    },

    cancel(id: string, expectedVersion: number) {
      return write('cancel', () => api.cancel(id, expectedVersion))
    },

    cancelRestore: invalidateReads,

    // 수정·취소의 결과가 불명확하면 GET 조회만으로 쓰기 완료를 단정하지 않는다.
    // 서버 계약에 맞춰 결과를 확인한 후 새 controller를 만든다.
    get requiresReconciliation() { return unresolved !== null },
  }
}
