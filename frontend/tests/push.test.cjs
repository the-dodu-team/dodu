const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs'), path = require('node:path'), ts = require('typescript')
function load(name) {
  const exports = {}
  new Function('exports', ts.transpileModule(fs.readFileSync(path.join(__dirname, '../src/features/notifications/', name + '.ts'), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS },
  }).outputText)(exports)
  return exports
}
const { createPushController } = load('pushController')
const { readPushConfig, pushDestination } = load('pushConfig')
function deferred() { let resolve; const promise = new Promise(r => { resolve = r }); return { promise, resolve } }
function setup(extra = {}) {
  let owner = 'synthetic-session-a', permission = 'granted', listener, token = 'synthetic-push-a'
  const states = [], calls = [], notices = []
  const driver = { supported: async () => true, token: async () => token,
    listen: fn => { listener = fn; return () => { listener = undefined } }, remove: async () => {} }
  const controller = createPushController({ configured: true, missing: false, session: () => owner,
    permission: () => permission, driver: async () => driver,
    subscribe: async (...args) => { calls.push(args) }, state: value => states.push(value), receive: value => notices.push(value), ...extra })
  return { controller, driver, states, calls, notices, owner: value => { owner = value }, permission: value => { permission = value },
    token: value => { token = value }, emit: value => listener?.(value) }
}
test('FCM 등록은 권한 허용과 현재 세션에 한해 수행하고 같은 토큰은 중복 저장하지 않는다', async () => {
  const s = setup(); await s.controller.sync(); await s.controller.sync()
  assert.deepEqual(s.calls, [['synthetic-session-a', 'synthetic-push-a']]); assert.equal(s.states.at(-1), 'ready')
  s.token('synthetic-push-b'); await s.controller.sync(); assert.equal(s.calls.length, 2)
})
test('등록 지연 중 로그아웃하면 토큰 결과를 서버에 저장하지 않는다', async () => {
  const s = setup(), gate = deferred(); s.driver.token = () => gate.promise
  const syncing = s.controller.sync(); await new Promise(r => setImmediate(r))
  const stopping = s.controller.pause(); s.owner(null); gate.resolve('synthetic-late-token')
  await Promise.all([syncing, stopping]); assert.equal(s.calls.length, 0); assert.ok(!s.states.includes('ready'))
})
test('이미 발송한 구독 PUT이 끝난 뒤에만 로그아웃 해지를 진행한다', async () => {
  const gate = deferred(), events = []
  const s = setup({ subscribe: async () => { events.push('put'); await gate.promise; events.push('stored') } })
  const sync = s.controller.sync(); await new Promise(r => setImmediate(r))
  const stop = s.controller.pause().then(() => events.push('delete'))
  assert.deepEqual(events, ['put']); gate.resolve(); await Promise.all([sync, stop]); assert.deepEqual(events, ['put', 'stored', 'delete'])
})
test('권한 거부·미설정·미지원은 토큰 발급이나 서버 등록을 수행하지 않는다', async () => {
  for (const extra of [{ configured: false }, { permission: () => 'denied' }, { driver: async () => ({ supported: async () => false }) }]) {
    const s = setup(extra); await s.controller.sync(); assert.equal(s.calls.length, 0); assert.ok(!s.states.includes('ready'))
  }
})
test('외부 오류 원문은 노출하지 않고 재시도로 연결을 복구한다', async () => {
  const s = setup(); s.driver.token = async () => { throw Error('sensitive-provider-details') }
  await s.controller.sync(); assert.equal(s.states.at(-1), 'error')
  s.driver.token = async () => 'synthetic-retry'; await s.controller.sync(); assert.equal(s.states.at(-1), 'ready')
})
test('이전 세션 콜백과 동일 messageId는 무시하고 열람 API를 자동 호출하지 않는다', async () => {
  const s = setup(); await s.controller.sync()
  s.emit({ messageId: 'synthetic-message' }); s.emit({ messageId: 'synthetic-message' }); assert.equal(s.notices.length, 1)
  s.owner('synthetic-session-b'); s.emit({ messageId: 'late' }); assert.equal(s.notices.length, 1)
  await s.controller.pause(); s.controller.resume(); await s.controller.sync()
  assert.equal(s.calls.at(-1)[0], 'synthetic-session-b')
})
test('알림 경로는 허용한 설문 UUID만 사용하고 외부 URL·코드 삽입을 거절한다', () => {
  assert.equal(pushDestination({ type: 'SURVEY_REMINDER', promiseId: '00000000-0000-4000-8000-000000000001' }), '/?self-report=00000000-0000-4000-8000-000000000001')
  for (const promiseId of ['https://invalid.example', '../secret', '<script>']) assert.equal(pushDestination({ type: 'SURVEY_REMINDER', promiseId }), '/')
  assert.equal(pushDestination({ type: 'START', url: 'https://invalid.example' }), '/')
})
test('공개 설정은 완전하게 주입해야 활성화하며 서버용 비밀값은 읽지 않는다', () => {
  const names = ['API_KEY', 'PROJECT_ID', 'MESSAGING_SENDER_ID', 'APP_ID', 'VAPID_KEY']
  assert.equal(readPushConfig({}).configured, false)
  assert.equal(readPushConfig({ VITE_FIREBASE_APP_ID: 'synthetic' }).missing, true)
  assert.equal(readPushConfig(Object.fromEntries(names.map(name => ['VITE_FIREBASE_' + name, 'synthetic']))).configured, true)
})
