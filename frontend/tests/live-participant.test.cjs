const assert = require('node:assert/strict')
const { test } = require('node:test')
const fs = require('node:fs')
const path = require('node:path')
const ts = require('typescript')
function load(file) {
  const module = { exports: {} }
  new Function('exports', ts.transpileModule(fs.readFileSync(path.resolve(__dirname, file), 'utf8'), { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText)(module.exports)
  return module.exports
}
const { createLiveSession } = load('../src/features/live/liveSession.ts')
const { createParticipantPromiseApi } = load('../src/api/participantPromiseApi.ts')
function storage() { const data = new Map(); return { getItem: k => data.get(k) ?? null, setItem: (k,v) => data.set(k,v), removeItem: k => data.delete(k) } }

test('기존 배포 세션·기기 키를 복원하고 로그아웃 때 구형 토큰도 지운다', () => {
  const tab = storage(), device = storage()
  device.setItem('dodu.participant.session-token', 'synthetic-legacy-session')
  device.setItem('dodu.participant.device-key', 'synthetic-legacy-device')
  const session = createLiveSession(tab, device)
  assert.equal(session.token(), 'synthetic-legacy-session')
  assert.equal(session.device(), 'synthetic-legacy-device')
  session.clear()
  assert.equal(session.token(), null)
  assert.equal(device.getItem('dodu.participant.session-token'), null)
})
test('기기 키·현재 탭 세션·결과 불명 요청은 재생성 후 복원하고 참가자 코드는 보관하지 않는다', () => {
  const tab = storage(), device = storage(); const first = createLiveSession(tab,device)
  const key = first.device(); first.enter('test-token')
  const pending = { kind: 'create', key: 'same-request', payload: { taskTitle: '테스트', toolType: 'BOOK', scheduledAt: '2026-11-03T00:00:00Z' } }
  first.mark(pending); const restored = createLiveSession(tab,device)
  assert.equal(restored.token(), 'test-token'); assert.equal(restored.device(), key); assert.deepEqual(restored.pending(),pending)
  restored.clear(); assert.equal(restored.token(),null); assert.equal(restored.pending(),null); assert.equal(restored.device(),key)
})
test('새 API 인스턴스에서도 결과 불명 생성 요청의 같은 멱등키를 사용한다', async () => {
  const keys = []; const result = { promiseId: 'p', taskTitle: '테스트', toolType: 'BOOK', scheduledAt: '2026-11-03T00:00:00Z', version: 0 }
  const fetcher = async (url, init) => { keys.push(init.headers.get('Idempotency-Key')); return Response.json(result) }
  for(let i=0;i<2;i++) await createParticipantPromiseApi({ getToken: () => 'test', fetcher }).prepareCreate(result, 'stable-key').send()
  assert.deepEqual(keys,['stable-key','stable-key'])
})
test('로그인·복원·생성·수정·취소가 실제 계약 경로와 버전을 사용한다', async () => {
  const calls = []; let token = null
  const row = { promiseId: 'p', taskTitle: '테스트', toolType: 'BOOK', scheduledAt: '2026-11-03T00:00:00Z', version: 0 }
  const user = { expiresAt: '2026-12-01T00:00:00Z', onboardingCompleted: false }
  const envelope = data => ({ status: 'success', data, serverTime: '2026-11-01T00:00:00Z' })
  const api = createParticipantPromiseApi({ getToken: () => token, fetcher: async (url,init) => {
    calls.push([url, init.method ?? 'GET', init.body ? JSON.parse(init.body) : null])
    if(url.endsWith('/sessions')) return Response.json(envelope({...user,sessionToken:'test-token'}))
    assert.equal(init.headers.get('Authorization'), 'Bearer test-token')
    if(url.endsWith('/me')) return Response.json(envelope(user))
    if(url.endsWith('/active')) return new Response(null)
    return Response.json(row)
  } })
  token = (await api.enter('test-code','device-key')).data.sessionToken
  await api.me(); await api.active(); await api.prepareCreate(row).send(); await api.reschedule('p',row.scheduledAt,0); await api.cancel('p',1)
  assert.deepEqual(calls.map(c => c.slice(0,2)), [
    ['/api/participant/sessions','POST'], ['/api/participant/me','GET'], ['/api/promises/active','GET'], ['/api/promises','POST'], ['/api/promises/p/schedule','PATCH'], ['/api/promises/p/cancel','POST']])
  assert.equal(calls[4][2].expectedVersion,0); assert.equal(calls[5][2].expectedVersion,1)
})
