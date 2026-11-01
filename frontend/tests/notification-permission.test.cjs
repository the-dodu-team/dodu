const assert = require('node:assert/strict')
const { test } = require('node:test')
const fs = require('node:fs')
const path = require('node:path')
const ts = require('typescript')
const mod = { exports: {} }
const source = fs.readFileSync(path.join(__dirname, '../src/features/onboarding/notificationPermission.ts'), 'utf8')
new Function('exports', ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText)(mod.exports)
const { createNotificationPermissionController, readNotificationState } = mod.exports
function env() { return { secure: true, ios: false, standalone: false, notification: { permission: 'default', requestPermission: async () => 'default' } } }
test('보안 연결·iOS 설치·미지원은 권한 요청을 하지 않는다', async () => {
  for (const [change, expected] of [[{ secure: false }, 'insecure'], [{ ios: true }, 'install-required'], [{ notification: undefined }, 'unsupported']]) {
    const value = { ...env(), ...change }
    if (value.notification) value.notification.requestPermission = () => { throw Error('호출하면 안 됨') }
    assert.equal(await createNotificationPermissionController(() => value).request(), expected)
  }
})
test('동시 클릭은 한 번만 요청하고 실제 브라우저 상태를 다시 읽는다', async () => {
  const value = env(); let resolve; let count = 0
  value.notification.requestPermission = () => { count++; return new Promise(r => { resolve = r }) }
  const control = createNotificationPermissionController(() => value)
  const first = control.request(); const second = control.request()
  assert.equal(first, second); assert.equal(count, 1)
  value.notification.permission = 'granted'; resolve('granted')
  assert.equal(await first, 'granted')
  value.notification.permission = 'denied'
  assert.equal(control.read(), 'denied')
  assert.equal(await control.request(), 'denied'); assert.equal(count, 1)
})
test('닫기와 오류는 허용으로 처리하지 않고 재시도할 수 있다', async () => {
  const value = env(); const control = createNotificationPermissionController(() => value)
  assert.equal(await control.request(), 'default')
  value.notification.requestPermission = async () => { throw Error('실패') }
  assert.equal(await control.request(), 'error')
  value.notification.requestPermission = async () => { value.notification.permission = 'granted'; return 'granted' }
  assert.equal(await control.request(), 'granted')
})
test('iOS 홈 화면 앱은 브라우저 권한을 읽는다', () => {
  assert.equal(readNotificationState({ ...env(), ios: true, standalone: true }), 'default')
})
