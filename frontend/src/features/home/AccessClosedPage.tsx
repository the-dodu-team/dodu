import { useEffect, useRef } from 'react'
import './home-states.css'

export default function AccessClosedPage() {
  const title = useRef<HTMLHeadingElement>(null)
  useEffect(() => { title.current?.focus() }, [])
  return <div className="access-end-page">
    <header className="app-header"><span className="app-header-logo">DODU</span></header>
    <main className="access-end-main" aria-labelledby="access-end-title">
      <div className="access-end-content">
        <div className="access-end-icon" aria-hidden="true">
          <svg viewBox="0 0 40 40"><circle cx="20" cy="20" r="17" fill="currentColor" /><path d="m12 20 5 5 11-12" fill="none" stroke="white" strokeWidth="2" /></svg>
        </div>
        <h1 ref={title} tabIndex={-1} id="access-end-title">행동검증이 종료됐어요</h1>
        <p>참여해 주셔서 감사합니다.<br />모든 참가자 접근이 종료되었으며, 결과나 후속 안내는<br />담당 연구자를 통해 전달돼요.</p>
      </div>
      <footer><button className="button" type="button" onClick={() => {
        // 브라우저가 닫기를 허용하지 않으면 종료 안내를 유지한다. 대체 문구는 디자인 확인 후 반영한다.
        window.close()
      }}>앱 종료하기</button></footer>
    </main>
  </div>
}
