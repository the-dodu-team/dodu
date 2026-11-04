const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const ts = require('typescript')
function load(file) {
  const filename = path.resolve(__dirname, file)
  const mod = { exports: {} }
  const localRequire = name => name.startsWith('.')
    ? load(path.relative(__dirname, path.resolve(path.dirname(filename), name + '.ts')))
    : require(name)
  new Function('exports', 'require', ts.transpileModule(fs.readFileSync(filename, 'utf8'),
    { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText)(mod.exports, localRequire)
  return mod.exports
}
const { createLiveSurveyApi, encodeLiveAnswers, canOpenLiveSurvey } = load('../src/api/liveSurveyApi.ts')
const row = { id: 'survey', questionnaireVersion: 'v1', lifecycleStatus: 'Prompted',
  eligibilityStatus: 'AuthCompleted', actualStarted: null, startOrder: null,
  startDelayBucket: null, noStartReason: null, availableAt: '2026-11-01T00:00:00Z',
  expiresAt: '2026-11-02T00:00:00Z', submittedAt: null }
test('실제 알림 경로에서 자기보고 약속 ID만 중복 제거한다', async () => {
  const api = createLiveSurveyApi(() => 'test-only', async (url, init) => {
    assert.equal(url, '/api/participant/notifications')
    assert.equal(init.headers.Authorization, 'Bearer test-only')
    return Response.json({ status: 'success', serverTime: '2026-11-01T01:00:00Z', data: [
      { promiseId: 'a', notificationType: 'SURVEY_REMINDER' },
      { promiseId: 'a', notificationType: 'SURVEY_REMINDER' },
      { promiseId: 'b', notificationType: 'START' },
    ] })
  })
  assert.deepEqual(await api.candidates(), ['a'])
})
test('알림 응답 도중 세션이 바뀌면 결과를 사용하지 않는다', async () => {
  let token = 'first'
  const api = createLiveSurveyApi(() => token, async () => {
    token = 'second'
    return Response.json({ status: 'success', data: [] })
  })
  await assert.rejects(api.candidates(), { kind: 'stale' })
})
test('자기보고 시간 경계와 종료·비대상 상태를 차단한다', () => {
  const start = Date.parse(row.availableAt), end = Date.parse(row.expiresAt)
  assert.equal(canOpenLiveSurvey(row, start - 1), false)
  assert.equal(canOpenLiveSurvey(row, start), true)
  assert.equal(canOpenLiveSurvey(row, end), false)
  for (const lifecycleStatus of ['Skipped', 'Submitted', 'Expired']) {
    assert.equal(canOpenLiveSurvey({ ...row, lifecycleStatus }, start), false)
  }
  for (const eligibilityStatus of ['TechFailed', 'Cancelled', 'Withdrawn']) {
    assert.equal(canOpenLiveSurvey({ ...row, eligibilityStatus }, start), false)
  }
})
test('실제 서버 경로로 제출하고 건너뛰기를 별도 전송한다', async () => {
  const calls = []
  const api = createLiveSurveyApi(() => 'test-only', async (url, init) => {
    calls.push({ url, init })
    return Response.json({ ...row, lifecycleStatus: url.endsWith('/skip') ? 'Skipped' : 'Submitted' })
  })
  const payload = encodeLiveAnswers({ started: 'yes', order: 'after', delay: 'within-5', reason: null })
  await api.submit('p', payload)
  await api.skip('p')
  assert.equal(calls[0].url, '/api/promises/p/validation-survey')
  assert.deepEqual(JSON.parse(calls[0].init.body), { actualStarted: true, startOrder: 'after', startDelayBucket: 'within-5', noStartReason: null })
  assert.equal(calls[1].url, '/api/promises/p/validation-survey/skip')
  assert.equal(calls[1].init.body, undefined)
  assert.deepEqual(encodeLiveAnswers({ started: 'no', order: 'after', delay: 'within-5', reason: 'condition' }),
    { actualStarted: false, startOrder: null, startDelayBucket: null, noStartReason: 'condition' })
  assert.throws(() => encodeLiveAnswers({ started: 'unknown', order: null, delay: null, reason: null }))
})