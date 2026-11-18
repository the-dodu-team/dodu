import { Component } from 'react'
import type { ReactNode } from 'react'

export default class AppErrorBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false }
  static getDerivedStateFromError() { return { failed: true } }
  render() {
    if (this.state.failed) return <main className="app-error-fallback">
      <h1>화면을 표시하지 못했어요</h1>
      <p role="alert">일시적인 문제가 생겼어요. 저장 중이었다면 다시 접속한 뒤 처리 결과를 확인해 주세요.</p>
      <button className="button" type="button" onClick={() => window.location.reload()}>다시 접속하기</button>
    </main>
    return this.props.children
  }
}
