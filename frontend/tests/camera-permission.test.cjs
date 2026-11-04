const assert = require('node:assert/strict')
const { test } = require('node:test')
const fs = require('node:fs')
const path = require('node:path')
const ts = require('typescript')
const mod = { exports: {} }
const source = fs.readFileSync(path.join(__dirname, '../src/features/onboarding/cameraPermission.ts'), 'utf8')
new Function('exports', ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText)(mod.exports)
const { requestCameraPermission, cameraRecoveryMessage } = mod.exports

test('실제 영상 권한을 요청하고 모든 트랙을 즉시 종료한다', async () => {
  let stops = 0
  const state = await requestCameraPermission({ secure: true, open: async constraints => {
    assert.deepEqual(constraints, { video: true, audio: false })
    return { getTracks: () => [{ stop: () => stops++ }, { stop: () => stops++ }] }
  } })
  assert.equal(state, 'granted'); assert.equal(stops, 2)
})

test('미지원·비보안 환경에서 카메라를 요청하거나 허용으로 처리하지 않는다', async () => {
  assert.equal(await requestCameraPermission({ secure: true }), 'unsupported')
  assert.equal(await requestCameraPermission({ secure: false, open: () => { throw Error('must not call') } }), 'insecure')
})

test('거부·기기 없음·장치 사용 중·일반 실패를 구분하고 내부 예외를 표시하지 않는다', async () => {
  for (const [name, expected] of [['NotAllowedError','denied'], ['NotFoundError','unavailable'],
    ['NotReadableError','busy'], ['AbortError','busy'], ['Error','error']]) {
    const state = await requestCameraPermission({ secure: true, open: async () => {
      const error = new Error('private-device-details'); error.name = name; throw error
    } })
    assert.equal(state, expected)
    assert.doesNotMatch(cameraRecoveryMessage(state), /private-device-details/)
    assert.ok(cameraRecoveryMessage(state).length > 10)
  }
})
