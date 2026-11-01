const assert = require('node:assert/strict')
const { test } = require('node:test')
const fs = require('node:fs')
const path = require('node:path')
const ts = require('typescript')
const cache = new Map()
function load(file) {
  if (!path.extname(file)) file += '.ts'
  if (cache.has(file)) return cache.get(file).exports
  const mod = { exports: {} }
  cache.set(file, mod)
  const code = ts.transpileModule(fs.readFileSync(file, 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText
  new Function('require', 'module', 'exports', code)(
    name => name.startsWith('.') ? load(path.resolve(path.dirname(file), name)) : require(name), mod, mod.exports)
  return mod.exports
}
const { createParticipantPromiseApi, classifyParticipantPromiseError } = load(path.resolve(__dirname, '../src/api/participantPromiseApi.ts'))
const { createParticipantPromiseController } = load(path.resolve(__dirname, '../src/api/participantPromiseController.ts'))
const payload = { taskTitle: '작업', toolType: 'COMPUTER', scheduledAt: '2026-11-02T09:00:00+09:00' }
const saved = { ...payload, promiseId: 'test-promise', version: 0 }
const state = { expiresAt: '2026-12-01T00:00:00Z', onboardingCompleted: true }
const envelope = data => ({ status: 'success', data, serverTime: '2026-11-01T00:00:00Z' })
const json = (body, status = 200) => new Response(JSON.stringify(body), { status })
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r }); return { promise, resolve } }

test('참가자 envelope와 빈 active 응답을 구분한다', async () => {
  const api = createParticipantPromiseApi({ getToken: () => 'test-token', fetcher: async (url, init) => {
    if (url.endsWith('/sessions')) {
      assert.equal(init.headers.has('Authorization'), false)
      return json(envelope({ ...state, sessionToken: 'new-test-token' }))
    }
    assert.equal(init.headers.get('Authorization'), 'Bearer test-token')
    return url.endsWith('/me') ? json(envelope(state)) : new Response(null)
  } })
  assert.equal((await api.enter('test-code', 'test-device-key')).data.sessionToken, 'new-test-token')
  assert.equal((await api.me()).data.onboardingCompleted, true)
  assert.equal((await api.active()).data, null)
})

test('중복 저장은 하나의 요청을 공유하고 서버 성공 전 완료되지 않는다', async () => {
  const pending = deferred(); let count = 0; let completed = false
  const api = createParticipantPromiseApi({ getToken: () => 'test-token', fetcher: async () => { count++; return pending.promise } })
  const request = api.prepareCreate(payload)
  const first = request.send(); const second = request.send()
  first.then(() => { completed = true })
  assert.equal(first, second); assert.equal(count, 1); assert.equal(completed, false)
  pending.resolve(json(saved)); await first
  assert.equal(completed, true)
  await request.send(); assert.equal(count, 1)
})

test('응답 본문 수신 실패도 결과 불명으로 분류하고 재시도 멱등키를 유지한다', async () => {
  const keys = []; let count = 0
  const api = createParticipantPromiseApi({ getToken: () => 'test-token', fetcher: async (_, init) => {
    keys.push(init.headers.get('Idempotency-Key'))
    if (++count === 1) return { status: 200, text: async () => { throw new TypeError('Connection lost') } }
    return json(saved)
  } })
  const request = api.prepareCreate(payload)
  await assert.rejects(request.send(), e => e.outcomeUnknown && classifyParticipantPromiseError(e) === 'network')
  await request.send(); assert.equal(keys[0], keys[1])
})

test('충돌·인증·권한·서버 오류를 구분한다', async () => {
  for (const [status, kind] of [[409, 'conflict'], [401, 'authentication'], [403, 'permission'], [500, 'technical']]) {
    const api = createParticipantPromiseApi({ getToken: () => 'test-token', fetcher: async () => json({ status: 'error', error: { code: 'TEST_ERROR' } }, status) })
    await assert.rejects(api.active(), e => classifyParticipantPromiseError(e) === kind)
  }
})

test('잘못된 응답을 저장 성공으로 취급하지 않는다', async () => {
  const api = createParticipantPromiseApi({ getToken: () => 'test-token', fetcher: async () => json({ unexpected: true }) })
  await assert.rejects(api.prepareCreate(payload).send(), e => e.code === 'INVALID_RESPONSE' && e.outcomeUnknown)
})

test('세션 교체 후 늦은 응답과 기존 저장 요청 재사용을 막는다', async () => {
  let token = 'old-test-token'; const pending = deferred()
  const api = createParticipantPromiseApi({ getToken: () => token, fetcher: () => pending.promise })
  const request = api.prepareCreate(payload); const result = request.send()
  token = 'new-test-token'; pending.resolve(json(saved))
  await assert.rejects(result, e => e.code === 'STALE_SESSION')
  await assert.rejects(request.send(), e => e.code === 'STALE_SESSION')
})

test('취소된 조회는 fetch가 늦게 성공해도 결과를 반환하지 않는다', async () => {
  const pending = deferred(); const abort = new AbortController()
  const api = createParticipantPromiseApi({ getToken: () => 'test-token', fetcher: () => pending.promise })
  const result = api.active(abort.signal); abort.abort(); pending.resolve(json(saved))
  await assert.rejects(result, e => e.name === 'AbortError')
})

test('새 복원 요청이 먼저 끝나면 이전 복원 결과를 폐기한다', async () => {
  const pending = deferred(); let count = 0
  const controller = createParticipantPromiseController({ getToken: () => 'test-token', fetcher: async url => {
    if (++count === 1) return pending.promise
    return url.endsWith('/me') ? json(envelope(state)) : json(saved)
  } })
  const first = controller.restore(); const rejection = assert.rejects(first, e => e.name === 'AbortError')
  const second = await controller.restore()
  assert.equal(second.activePromise.data.scheduledAt, payload.scheduledAt)
  pending.resolve(json(envelope(state))); await rejection
})

test('생성 결과 불명 상태에서는 다른 입력을 저장하지 않고 같은 요청만 재시도한다', async () => {
  let count = 0
  const controller = createParticipantPromiseController({ getToken: () => 'test-token', fetcher: async () => {
    if (++count === 1) throw new TypeError('Connection lost')
    return json(saved)
  } })
  await assert.rejects(controller.create(payload))
  assert.equal(controller.requiresReconciliation, true)
  await assert.rejects(controller.create({ ...payload, taskTitle: '다른 작업' }), e => e.code === 'REQUEST_IN_PROGRESS')
  await controller.create(payload)
  assert.equal(controller.requiresReconciliation, false)
})

test('수정·취소 중복 요청과 결과 불명 후 자동 재전송을 막는다', async () => {
  const pending = deferred(); let count = 0
  const controller = createParticipantPromiseController({ getToken: () => 'test-token', fetcher: async () => { count++; return pending.promise } })
  const first = controller.cancel('test-promise', 0)
  await assert.rejects(controller.reschedule('test-promise', payload.scheduledAt, 0), e => e.code === 'REQUEST_IN_PROGRESS')
  pending.resolve(json(saved)); await first; assert.equal(count, 1)
  const uncertain = createParticipantPromiseController({ getToken: () => 'test-token', fetcher: async () => { throw new TypeError('Connection lost') } })
  await assert.rejects(uncertain.cancel('test-promise', 0))
  await assert.rejects(uncertain.cancel('test-promise', 0), e => e.code === 'REQUEST_IN_PROGRESS')
})
