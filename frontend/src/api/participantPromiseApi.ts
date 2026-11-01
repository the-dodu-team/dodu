// origin/dev ac2755c의 실행 컨트롤러 기준이다. 미구현 재생성 API는 호출하지 않는다.
export type PromisePayload = { taskTitle: string; toolType: 'COMPUTER' | 'BOOK'; scheduledAt: string }
export type PromiseResponse = PromisePayload & { promiseId: string; version: number }
export type ParticipantState = { expiresAt: string; onboardingCompleted: boolean }
export type ParticipantSession = ParticipantState & { sessionToken: string }
export type ApiResult<T> = { data: T; serverTime: string | null }
export type ApiErrorKind = 'authentication' | 'permission' | 'conflict' | 'network' | 'validation' | 'technical' | 'cancelled'

export function classifyParticipantPromiseError(error: unknown): ApiErrorKind {
  if (error instanceof DOMException && error.name === 'AbortError') return 'cancelled'
  if (!(error instanceof ParticipantPromiseApiError)) return 'technical'
  if (error.code === 'STALE_SESSION' || error.httpStatus === 401) return 'authentication'
  if (error.httpStatus === 403) return 'permission'
  if (error.httpStatus === 409) return 'conflict'
  if (error.code === 'NETWORK_ERROR') return 'network'
  if (error.httpStatus === 400 || error.httpStatus === 422) return 'validation'
  return 'technical'
}

export class ParticipantPromiseApiError extends Error {
  readonly code: string
  readonly httpStatus: number
  readonly outcomeUnknown: boolean

  constructor(code: string, httpStatus: number, outcomeUnknown = false) {
    // UI 문구는 확정된 화면 문구에 매핑한다. 서버 원문·토큰은 로그에 출력하지 않는다.
    super(code)
    this.name = 'ParticipantPromiseApiError'
    this.code = code
    this.httpStatus = httpStatus
    this.outcomeUnknown = outcomeUnknown
  }
}

type JsonObject = Record<string, unknown>
function object(value: unknown): value is JsonObject {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}
function participant(value: unknown): value is ParticipantState {
  return object(value) && typeof value.onboardingCompleted === 'boolean' &&
    typeof value.expiresAt === 'string' && Number.isFinite(Date.parse(value.expiresAt))
}
function session(value: unknown): value is ParticipantSession {
  return participant(value) && 'sessionToken' in value && typeof value.sessionToken === 'string' && value.sessionToken.length > 0
}
function promise(value: unknown): value is PromiseResponse {
  return object(value) && typeof value.promiseId === 'string' && value.promiseId.length > 0 &&
    typeof value.taskTitle === 'string' && ['COMPUTER', 'BOOK'].includes(String(value.toolType)) &&
    typeof value.scheduledAt === 'string' && Number.isFinite(Date.parse(value.scheduledAt)) &&
    typeof value.version === 'number' && Number.isSafeInteger(value.version) && value.version >= 0
}

export function createParticipantPromiseApi(options: {
  // 토큰은 코드에 넣거나 이 모듈에서 저장하지 않는다.
  getToken: () => string | null
  fetcher?: typeof fetch
}) {
  const fetcher = options.fetcher ?? fetch

  async function request<T>(path: string, validate: (value: unknown) => value is T,
    init: RequestInit, envelope: boolean, authenticated = true): Promise<ApiResult<T>> {
    const headers = new Headers(init.headers)
    headers.set('Accept', 'application/json')
    if (init.body) headers.set('Content-Type', 'application/json')
    const token = options.getToken()
    if (authenticated) {
      if (!token) throw new ParticipantPromiseApiError('ACCESS_REQUIRED', 401)
      headers.set('Authorization', `Bearer ${token}`)
    }
    const writing = init.method !== undefined && init.method !== 'GET'
    let response: Response
    try {
      response = await fetcher(path, { ...init, headers, cache: 'no-store', redirect: 'error' })
    } catch (error) {
      if (init.signal?.aborted) throw error
      // 연결이 끊겨도 서버에서 저장되었을 수 있으므로 자동 재전송하지 않는다.
      throw new ParticipantPromiseApiError('NETWORK_ERROR', 0, writing)
    }
    let raw: string
    try {
      raw = await response.text()
    } catch (error) {
      if (init.signal?.aborted) throw error
      throw new ParticipantPromiseApiError('NETWORK_ERROR', response.status, writing)
    }
    // 요청 취소나 세션 교체 후 도착한 응답은 현재 화면에 사용하지 않는다.
    init.signal?.throwIfAborted()
    if (authenticated && options.getToken() !== token) {
      throw new ParticipantPromiseApiError('STALE_SESSION', 401, writing)
    }
    let body: unknown = null
    try { body = raw.trim() ? JSON.parse(raw) : null } catch {
      throw new ParticipantPromiseApiError('INVALID_RESPONSE', response.status, writing)
    }
    if (!response.ok || (object(body) && body.status === 'error')) {
      const serverError = object(body) && object(body.error) ? body.error : null
      const code = serverError && typeof serverError.code === 'string' ? serverError.code : `HTTP_${response.status}`
      throw new ParticipantPromiseApiError(code, response.status, writing && response.status >= 500)
    }
    let data = body
    let serverTime: string | null = null
    if (envelope) {
      if (!object(body) || body.status !== 'success' || typeof body.serverTime !== 'string' ||
          !Number.isFinite(Date.parse(body.serverTime))) {
        throw new ParticipantPromiseApiError('INVALID_RESPONSE', response.status, writing)
      }
      data = body.data
      serverTime = body.serverTime
    }
    if (!validate(data)) throw new ParticipantPromiseApiError('INVALID_RESPONSE', response.status, writing)
    return { data, serverTime }
  }

  function versionBody(expectedVersion: number) {
    if (!Number.isSafeInteger(expectedVersion) || expectedVersion < 0) throw new Error('Invalid expectedVersion')
    return { expectedVersion }
  }

  return {
    // 참가자 코드는 저장하지 않는다. deviceKey는 호출자가 기기별로 유지한다.
    enter: (code: string, deviceKey: string, signal?: AbortSignal) => request('/api/participant/sessions', session,
      { method: 'POST', body: JSON.stringify({ code, deviceKey }), signal }, true, false),
    me: (signal?: AbortSignal) => request('/api/participant/me', participant, { signal }, true),
    // 약속 컨트롤러는 현재 envelope와 serverTime을 반환하지 않는다.
    active: (signal?: AbortSignal) => request('/api/promises/active',
      (value): value is PromiseResponse | null => value === null || promise(value), { signal }, false),
    prepareCreate: (payload: PromisePayload, requestKey: string = crypto.randomUUID()) => {
      const ownerToken = options.getToken()
      const key = requestKey
      const body = JSON.stringify(payload)
      let inFlight: Promise<ApiResult<PromiseResponse>> | null = null
      let completed: ApiResult<PromiseResponse> | null = null
      return {
        // 같은 저장 시도의 재시도는 같은 객체를 사용한다. 입력이 바뀌면 새 객체를 만든다.
        send(signal?: AbortSignal): Promise<ApiResult<PromiseResponse>> {
          if (signal?.aborted) return Promise.reject(signal.reason)
          if (!ownerToken || ownerToken !== options.getToken()) {
            return Promise.reject(new ParticipantPromiseApiError('STALE_SESSION', 401))
          }
          if (completed) return Promise.resolve(completed)
          if (inFlight) return inFlight
          inFlight = request('/api/promises', promise, {
            method: 'POST', headers: { 'Idempotency-Key': key }, body, signal,
          }, false).then(result => { completed = result; return result }).finally(() => { inFlight = null })
          return inFlight
        },
      }
    },
    reschedule: (id: string, scheduledAt: string, expectedVersion: number, signal?: AbortSignal) =>
      request(`/api/promises/${encodeURIComponent(id)}/schedule`, promise, {
        method: 'PATCH', body: JSON.stringify({ scheduledAt, ...versionBody(expectedVersion) }), signal,
      }, false),
    cancel: (id: string, expectedVersion: number, signal?: AbortSignal) =>
      request(`/api/promises/${encodeURIComponent(id)}/cancel`, promise, {
        method: 'POST', body: JSON.stringify(versionBody(expectedVersion)), signal,
      }, false),
  }
}
