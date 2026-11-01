const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { createRequire } = require('node:module')
const root = path.resolve(__dirname, '..')
const req = createRequire(root + '/package.json')
const ts = req('typescript')
const cache = new Map()
function load(file) {
  if (!path.extname(file)) file = ['.tsx', '.ts'].map(ext => file + ext).find(fs.existsSync)
  if (/\.(svg|png|css)$/.test(file)) return { default: file }
  if (cache.has(file)) return cache.get(file).exports
  const mod = { exports: {} }; cache.set(file, mod)
  const code = ts.transpileModule(fs.readFileSync(file, 'utf8').replaceAll('import.meta.env.DEV', 'true'), { compilerOptions: {
    module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX, target: ts.ScriptTarget.ES2022,
  }}).outputText
  new Function('require', 'module', 'exports', code)(name => name.startsWith('.')
    ? load(path.resolve(path.dirname(file), name)) : req(name), mod, mod.exports)
  return mod.exports
}

const { getHomeState: state, interventionClosed: closed } = load(root + '/src/features/home/homeState.ts')
const { getFinalPromiseRecord: final } = load(root + '/src/features/promise/promiseHistory.ts')
const { canAnswer } = load(root + '/src/features/self-report/selfReportModel.ts')
const { createPhotoSession, photoReducer } = load(root + '/src/features/photo-auth/photoAuthModel.ts')
const start = Date.parse('2026-10-21T09:00:00+09:00')
const p = { taskName: '포트폴리오 정리', date: '2026-10-21', startTime: '09:00', tool: 'computer', status: 'pending' }
assert.equal(state(null, start), 'empty')
assert.equal(state(p, start - 1), 'scheduled')
assert.equal(state(p, start), 'available')
assert.equal(state(p, start + 30 * 60_000 - 1), 'available')
assert.equal(state(p, start + 30 * 60_000), 'ended')
assert.equal(state({ ...p, status: 'evaluating' }, start + 31 * 60_000), 'evaluating')
for (const status of ['techFailed', 'cancelled', 'withdrawn', 'permissionBlocked']) {
  assert.equal(final({ ...p, status }, start + 31 * 60_000), null)
}
assert.equal(closed(start - 1, start), false)
assert.equal(closed(start, start), true)
assert.equal(closed(start), false)
assert.equal(canAnswer({ ...p, status: 'verified' }, undefined, Date.parse('2026-10-21T18:00:00+09:00')), true)
let s = createPhotoSession(p)
s = photoReducer(s, { type: 'capture', now: start })
s = photoReducer(s, { type: 'outcome', value: 'hold' })
s = photoReducer(s, { type: 'submit', now: start + 60_000 })
s = photoReducer(s, { type: 'tick', now: start + 40 * 60_000 })
assert.equal(s.stage, 'evaluating')
s = photoReducer(s, { type: 'result', outcome: 'success', attempt: s.attempt, now: start + 40 * 60_000 })
assert.equal(s.stage, 'success')
console.log('PASS: home states, time boundaries, exclusions, late result, independent self-report')
