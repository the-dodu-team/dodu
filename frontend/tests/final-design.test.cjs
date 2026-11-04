const assert = require('node:assert/strict')
const { test } = require('node:test')
const fs = require('node:fs')
const path = require('node:path')
const ts = require('typescript')
function load(relative) {
  const exports = {}
  const source = fs.readFileSync(path.join(__dirname, relative), 'utf8')
  new Function('exports', ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS },
  }).outputText)(exports)
  return exports
}
const { photoPresentation, photoCanSubmit } = load('../src/features/photo-auth/livePhotoPresentation.ts')
const start = Date.parse('2026-11-05T10:00:00+09:00'), end = start + 30 * 60000

test('전체 약속 이력은 같은 날짜 여러 건과 기술 종료·취소를 구분해 보존한다', () => {
  const { savedPromiseHistory } = load('../src/features/promise/savedPromiseHistory.ts')
  const rows = ['AuthCompleted', 'AuthIncomplete', 'TechFailed', 'Cancelled', 'Withdrawn'].map((finalOutcome, i) => ({
    promiseId: 'p' + i, taskTitle: 'task' + i, scheduledAt: '2026-11-05T01:00:00Z', toolType: 'BOOK', version: 0,
    lifecycleStatus: 'Closed', finalOutcome,
  }))
  const result = savedPromiseHistory(rows)
  assert.equal(result.length, 5)
  assert.deepEqual(result.map(row => row.status), ['verified', 'unverified', 'technical', 'cancelled', 'withdrawn'])
  assert.equal(new Set(result.map(row => row.id)).size, 5)
})

test('마감 전 접수한 사진은 마감 후에도 판정 중으로 표시하고 재제출을 막는다', () => {
  for (const now of [start, end - 1, end, end + 3600000]) {
    assert.equal(photoPresentation('Evaluating', true, now, start), 'evaluating')
    assert.equal(photoCanSubmit('evaluating', now, start), false)
  }
  assert.equal(photoPresentation('Completed', false, end + 1, start), 'success')
})
test('신규 제출은 T 이상 T+30 미만이며 시간 정보를 모르면 제출하지 않는다', () => {
  assert.equal(photoCanSubmit('review', start - 1, start), false)
  assert.equal(photoCanSubmit('review', start, start), true)
  assert.equal(photoCanSubmit('review', end - 1, start), true)
  assert.equal(photoCanSubmit('review', end, start), false)
  assert.equal(photoCanSubmit('review', start, NaN), false)
  assert.equal(photoPresentation('READY', false, start, NaN), 'technical')
})
test('거절 재시도·시간 종료·기술 종료를 분리하고 기술 실패를 미완료로 바꾸지 않는다', () => {
  assert.equal(photoPresentation('Rejected', true, end - 1, start), 'rejected')
  assert.equal(photoPresentation('Rejected', true, end, start), 'ended')
  assert.equal(photoPresentation('TechFailed', true, end + 1, start), 'technical-final')
  assert.equal(photoCanSubmit('technical-final', start, start), false)
  assert.equal(photoPresentation('CHECK_FAILED', true, end + 1, start), 'technical')
  assert.equal(photoPresentation('READY', true, start, start), 'review')
})

test('온보딩 완료와 로그아웃 구독 해제는 인증된 현재 참가자 경로만 사용한다', async () => {
  const { createDoduApi } = load('../src/api/doduApi.ts')
  const previous = global.fetch
  const calls = []
  global.fetch = async (url, init) => {
    calls.push({ url, method: init.method, headers: init.headers, body: init.body })
    return new Response(JSON.stringify({ status: 'success', data: { onboardingCompleted: true } }))
  }
  try {
    const api = createDoduApi('synthetic-only-session', 'https://backend.invalid')
    assert.equal((await api.completeOnboarding()).onboardingCompleted, true)
    await api.unsubscribeNotifications()
    assert.deepEqual(calls.map(({ url, method }) => [url, method]), [
      ['https://backend.invalid/api/participant/onboarding', 'PUT'],
      ['https://backend.invalid/api/participant/notification-subscription', 'DELETE'],
    ])
    for (const call of calls) {
      assert.equal(call.headers.Authorization, 'Bearer synthetic-only-session')
      assert.equal(call.body, undefined)
      assert.doesNotMatch(call.url, /account|withdraw/)
    }
  } finally { global.fetch = previous }
})
