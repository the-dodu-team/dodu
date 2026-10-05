import Button from './components/Button'

export default function ComponentPreview() {
  return (
    <main>
      <h1>공통 컴포넌트 확인</h1>
      <p>개발용 화면입니다. 실제 예약이나 인증은 실행하지 않습니다.</p>

      <section aria-labelledby="button-preview-title">
        <h2 id="button-preview-title">버튼</h2>

        <h3>정상</h3>
        <Button>내용 확인</Button>

        <h3>로딩 예시</h3>
        <Button disabled>확인 중…</Button>

        <h3>비활성</h3>
        <p>필수 입력을 완료하면 진행할 수 있습니다.</p>
        <Button disabled>내용 확인</Button>
      </section>
    </main>
  )
}