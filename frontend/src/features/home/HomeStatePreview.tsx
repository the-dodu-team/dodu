import { useState } from 'react'
import type { ReactNode } from 'react'
import type { PreviewPromise } from '../promise/types'
import { createPhotoSession } from '../photo-auth/photoAuthModel'
import type { PhotoSession } from '../photo-auth/photoAuthModel'
import './home-states.css'

export type HomeSeed = {
  initialPromise?: PreviewPromise | null
  initialPhotoSession?: PhotoSession | null
  interventionEndsAt?: number
  accessClosed?: boolean
}

const scenarios = [
  ['empty', '활성 없음'],
  ['scheduled', '예약'],
  ['available', '인증 가능'],
  ['evaluating', '판정 중'],
  ['verified', '종료 · 인증 완료'],
  ['unverified', '종료 · 인증 미완료'],
  ['intervention', '공식 개입 종료 · 자기보고 가능'],
  ['waiting', '공식 개입 종료 · 접수 사진 판정 대기'],
  ['access', '서비스 접근 종료'],
] as const

function seedFor(state: string): HomeSeed {
  if (state === 'empty') return {}
  if (state === 'access') return { accessClosed: true }

  const now = Date.now()

  // 인증 미완료와 마감 후 판정 대기는 인증 시간이 끝난 시각으로 설정한다.
  const offsetMinutes =
    state === 'scheduled'
      ? 60
      : state === 'intervention'
        ? -24 * 60
        : state === 'waiting' || state === 'unverified'
          ? -35
          : -5

  let start = now + offsetMinutes * 60_000

  if (state === 'intervention') {
    const local = new Date(now + 9 * 60 * 60_000)
    const hour = local.getUTCHours()

    if (hour < 18) {
      local.setUTCDate(local.getUTCDate() - (hour >= 11 ? 1 : 2))
    }

    const date = local.toISOString().slice(0, 10)
    start = Date.parse(
      date + (hour >= 18 ? 'T09:00:00+09:00' : 'T14:00:00+09:00'),
    )
  }

  const kst = new Date(start + 9 * 60 * 60_000).toISOString()

  const promise: PreviewPromise = {
    taskName: '포트폴리오 정리',
    date: kst.slice(0, 10),
    startTime: kst.slice(11, 16),
    tool: 'computer',
    status:
      state === 'verified' || state === 'intervention'
        ? 'verified'
        : state === 'unverified'
          ? 'unverified'
          : 'pending',
  }

  if (state === 'evaluating' || state === 'waiting') {
    const session = createPhotoSession(promise)

    return {
      initialPromise: promise,
      interventionEndsAt: state === 'waiting' ? now - 60_000 : undefined,
      initialPhotoSession: {
        ...session,
        stage: 'evaluating',
        attempt: 1,
        receivedAt: session.startsAt + 60_000,
        outcome: 'hold',
      },
    }
  }

  const terminal = createPhotoSession(promise)
  const initialPhotoSession: PhotoSession = {
    ...terminal,
    stage: promise.status === 'verified' ? 'success' : 'expired',
    resolvedAt: now,
  }

  return {
    initialPhotoSession: ['verified', 'unverified', 'intervention'].includes(
      state,
    )
      ? initialPhotoSession
      : null,
    initialPromise: promise,
    interventionEndsAt: state === 'intervention' ? now - 60_000 : undefined,
  }
}

export default function HomeStatePreview({
  renderHome,
}: {
  renderHome: (seed: HomeSeed) => ReactNode
}) {
  const [selection, setSelection] = useState(() => ({
    state: 'empty',
    seed: seedFor('empty'),
  }))

  return (
    <>
      <aside className="home-state-dev">
        <label htmlFor="home-scenario">
          개발용 홈 상태 mock · 서버 저장 없음
        </label>
        <select
          id="home-scenario"
          value={selection.state}
          onChange={event => {
            const state = event.target.value
            setSelection({ state, seed: seedFor(state) })
          }}
        >
          {scenarios.map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
      </aside>
      <div key={selection.state}>{renderHome(selection.seed)}</div>
    </>
  )
}