import { useState } from 'react'
import Button from './components/Button'
import TextField from './components/TextField'
import EmptyState from './components/EmptyState'

import AppHeader from './components/AppHeader'

export default function ComponentPreview() {
  const [taskName, setTaskName] = useState('')
  const [showError, setShowError] = useState(false)
  const [buttonMessage, setButtonMessage] = useState('')

  return (
    // 이전: 앱 header가 <main> 안에 있어 최상위 헤더 랜드마크가 아니었다.
    <div className="component-preview">
      <AppHeader />
      <main>
      <h1>공통 컴포넌트 확인</h1>
      <p>개발용 화면입니다. 실제 예약이나 인증은 실행하지 않습니다.</p>

      <section aria-labelledby="button-preview-title">
        <h2 id="button-preview-title">버튼</h2>

        <h3>정상</h3>
        <Button onClick={() => setButtonMessage('버튼 클릭이 확인됐어요.')}>
          내용 확인
        </Button>
        <p role="status">{buttonMessage}</p>

        <h3>로딩 예시</h3>
        <Button disabled>확인 중…</Button>

        <h3>비활성</h3>
        <p>필수 입력을 완료하면 진행할 수 있습니다.</p>
        <Button disabled>내용 확인</Button>
      </section>

      <section aria-labelledby="input-preview-title">
        <h2 id="input-preview-title">입력·오류 안내</h2>

        <TextField
          id="preview-task-name"
          label="작업명"
          value={taskName}
          onChange={setTaskName}
          hint="예: 포트폴리오 정리, 알고리즘 공부"
          error={
            showError
              ? '오류 안내 예시입니다. 입력 내용을 확인해주세요.'
              : undefined
          }
        />

        <p>입력한 내용: {taskName || '아직 입력하지 않았어요.'}</p>

        <Button onClick={() => setShowError((previous) => !previous)}>
          {showError ? '오류 예시 숨기기' : '오류 예시 보기'}
        </Button>
      </section>

      <section aria-labelledby="empty-preview-title">
        <h2 id="empty-preview-title">빈 상태 안내</h2>
        <EmptyState
          title="아직 표시할 내용이 없어요"
          description="내용이 추가되면 이곳에 표시됩니다."
        />
      </section>
      </main>
    </div>
  )
}
