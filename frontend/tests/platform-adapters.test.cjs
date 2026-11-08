const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { createRequire } = require('node:module')
const root = path.resolve(__dirname, '..')
const req = createRequire(root + '/package.json')
const ts = req('typescript')
const cache = new Map()
function load(file) {
  if (!path.extname(file)) file += '.ts'
  if (cache.has(file)) return cache.get(file).exports
  const mod = { exports: {} }; cache.set(file, mod)
  const code = ts.transpileModule(fs.readFileSync(file, 'utf8'), { compilerOptions: {
    module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022,
  }}).outputText
  new Function('require', 'module', 'exports', code)(name => name.startsWith('.')
    ? load(path.resolve(path.dirname(file), name)) : req(name), mod, mod.exports)
  return mod.exports
}
const { createMemoryStorage } = load(root + '/src/platform/storage')
const { ParticipantSessionStore } = load(root + '/src/platform/participantSession')
const { createLiveSession } = load(root + '/src/features/live/liveSession')
;(async () => {
  const storage = createMemoryStorage()
  const session = new ParticipantSessionStore(storage)
  assert.equal(await session.getToken(), null)
  assert.equal(await session.getOrCreateDeviceKey(() => 'device-1'), 'device-1')
  assert.equal(await session.getOrCreateDeviceKey(() => 'device-2'), 'device-1')
  await session.saveToken('session-token')
  assert.equal(await session.getToken(), 'session-token')
  await session.clear()
  assert.equal(await session.getToken(), null)
  assert.equal(await session.getOrCreateDeviceKey(() => 'device-3'), 'device-3')
  const tab = createMemoryStorage(), device = createMemoryStorage()
  const live = createLiveSession(tab, device, () => 'injected-native-device')
  assert.equal(live.device(), 'injected-native-device')
  live.enter('synthetic-live-token')
  live.mark({ kind: 'cancel', id: 'synthetic-promise', version: 0 })
  assert.equal(live.token(), 'synthetic-live-token')
  assert.equal(live.pending().kind, 'cancel')
  live.clear()
  assert.equal(live.token(), null)
  assert.equal(live.pending(), null)
  assert.equal(live.device(), 'injected-native-device')
  assert.equal(createLiveSession(tab, device, () => 'must-not-replace-device').device(), 'injected-native-device')
  console.log('PASS: platform-neutral storage and participant session adapters')
})().catch(error => { console.error(error); process.exitCode = 1 })
