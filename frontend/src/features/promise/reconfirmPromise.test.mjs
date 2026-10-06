import assert from 'node:assert/strict'
import { test } from 'node:test'
import { reconfirmPromise } from './reconfirmPromise.ts'

const date = '2026-10-07'
const draft = { taskName: '공부', startTime: '18:00', tool: 'computer' }
const previous = { ...draft, date, status: 'verified' }

test('unchanged details preserve verified status and trim surrounding whitespace', () => {
  assert.equal(reconfirmPromise(previous, { ...draft, taskName: ' 공부 ' }, date).status, 'verified')
  assert.equal(reconfirmPromise(previous, draft, date).status, 'verified')
  assert.equal(reconfirmPromise({ ...previous, status: 'pending' }, draft, date).status, 'pending')
})

for (const changed of [
  { taskName: '다른 작업' }, { startTime: '19:00' }, { tool: 'book' },
]) {
  test(`changed ${Object.keys(changed)[0]} resets only preview status`, () => {
    assert.equal(reconfirmPromise(previous, { ...draft, ...changed }, date).status, 'pending')
    assert.equal(previous.status, 'verified')
  })
}

test('new date resets preview status', () => {
  assert.equal(reconfirmPromise(previous, draft, '2026-10-08').status, 'pending')
})

test('new promise starts pending', () => {
  assert.deepEqual(reconfirmPromise(null, draft, date), { ...draft, date, status: 'pending' })
})
