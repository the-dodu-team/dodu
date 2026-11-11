const assert = require('node:assert/strict')
const { test } = require('node:test')
const fs = require('node:fs')
const ts = require('node:module').createRequire(__dirname + '/../../frontend/package.json')('typescript')
const source = ts.transpileModule(fs.readFileSync(__dirname + '/../src/session/nativeSession.ts', 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText
const mod = { exports: {} }
new Function('exports', 'module', source)(mod.exports, mod)
const { createNativeSession } = mod.exports
function storage(initial = {}) {
  const values = new Map(Object.entries(initial))
  return { getItem: async key => values.get(key) ?? null, setItem: async (key, value) => { values.set(key, value) }, removeItem: async key => { values.delete(key) } }
}
test('restore gates synchronous token access and preserves installation identity on logout', async () => {
  const tokenStorage = storage(), deviceStorage = storage()
  const session = createNativeSession({ tokenStorage, deviceStorage, randomUuid: () => 'synthetic-device' })
  assert.equal(session.getToken(), null)
  assert.throws(() => session.getDeviceKey(), /SESSION_NOT_READY/)
  await session.initialize()
  await session.saveToken('synthetic-token')
  const reopened = createNativeSession({ tokenStorage, deviceStorage, randomUuid: () => 'must-not-replace' })
  await reopened.initialize()
  assert.equal(reopened.getToken(), 'synthetic-token')
  assert.equal(reopened.getDeviceKey(), 'synthetic-device')
  await reopened.clear()
  assert.equal(reopened.getToken(), null)
  assert.equal(reopened.getDeviceKey(), 'synthetic-device')
})
test('concurrent initialize generates one device and failed restore stays locked', async () => {
  let generated = 0
  const session = createNativeSession({ tokenStorage: storage(), deviceStorage: storage(), randomUuid: () => String(++generated) })
  await Promise.all([session.initialize(), session.initialize()])
  assert.equal(generated, 1)
  const broken = createNativeSession({ tokenStorage: { ...storage(), getItem: async () => { throw Error('synthetic-failure') } }, deviceStorage: storage(), randomUuid: () => 'unused' })
  await assert.rejects(broken.initialize())
  assert.equal(broken.isReady(), false)
  assert.equal(broken.getToken(), null)
})
test('logout waits for in-flight persistence and clears its result', async () => {
  let release
  const wait = new Promise(resolve => { release = resolve })
  const tokens = storage()
  const session = createNativeSession({ tokenStorage: { ...tokens, setItem: async (key, value) => { await wait; await tokens.setItem(key, value) } }, deviceStorage: storage(), randomUuid: () => 'synthetic-device' })
  await session.initialize()
  const saving = session.saveToken('synthetic-token')
  const clearing = session.clear()
  assert.equal(session.getToken(), null)
  release(); await Promise.all([saving, clearing])
  assert.equal(session.getToken(), null)
  assert.equal(await tokens.getItem('dodu.native.session.v1'), null)
})
test('persistence failure is propagated without accepting a new token', async () => {
  const session = createNativeSession({ tokenStorage: { ...storage(), setItem: async () => { throw Error('synthetic-failure') } }, deviceStorage: storage(), randomUuid: () => 'synthetic-device' })
  await session.initialize()
  await assert.rejects(session.saveToken('synthetic-token'))
  assert.equal(session.getToken(), null)
})
