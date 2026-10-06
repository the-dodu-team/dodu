import { useState } from 'react'
import AppHeader from './components/AppHeader'
import DoduHome from './DoduHome'
import PromiseFormPage from './features/promise/PromiseFormPage'
import PromiseConfirmPage from './features/promise/PromiseConfirmPage'
import PromiseHistoryPage from './features/promise/PromiseHistoryPage'
import { getKstDateKey } from './features/promise/formatPromise'
import { reconfirmPromise } from './features/promise/reconfirmPromise'
import type { PromiseDraft, PreviewPromise } from './features/promise/types'

type PreviewScreen = 'home' | 'write' | 'confirm' | 'history'

export default function App() {
  const [screen, setScreen] = useState<PreviewScreen>('home')
  const [draft, setDraft] = useState<PromiseDraft>({
    taskName: '',
    startTime: '',
    tool: 'computer',
  })
  const [promise, setPromise] = useState<PreviewPromise | null>(null)

  const todayPromise =
    promise?.date === getKstDateKey() ? promise : null

  function openEditor() {
    if (todayPromise) {
      setDraft({
        taskName: todayPromise.taskName,
        startTime: todayPromise.startTime,
        tool: todayPromise.tool,
      })
    }

    setScreen('write')
  }

  // API 연결 시 메모리 내 미리보기 처리를 서버 저장·조회 흐름으로 교체한다.
  // 서버 저장 성공을 확인한 뒤에만 완료를 표시하고, 실패 시 입력값을 유지한다.
  function showHomePreview() {
    if (!draft.taskName.trim() || !draft.startTime) return

    // 이전 미리보기 코드 보존: 같은 내용을 재확인해도 pending으로 초기화했다.
    // setPromise({
    //   ...draft,
    //   taskName: draft.taskName.trim(),
    //   date: getKstDateKey(),
    //   status: 'pending',
    // })
    const date = getKstDateKey()
    setPromise((previous) => reconfirmPromise(previous, draft, date))
    setScreen('home')
  }

  // 화면 확인용 상태 변경이다. 실제 사진 인증이나 서버 저장은 실행하지 않는다.
  function verifyPreviewPromise() {
    setPromise((previous) =>
      previous ? { ...previous, status: 'verified' } : previous,
    )
  }

  return (
    <main className="home-page">
      <AppHeader />

      {screen === 'home' && (
        <DoduHome
          promise={promise}
          onCreate={openEditor}
          onEdit={openEditor}
          onViewAll={() => setScreen('history')}
          onVerify={verifyPreviewPromise}
        />
      )}

      {screen === 'write' && (
        <PromiseFormPage
          draft={draft}
          setDraft={setDraft}
          isEditing={Boolean(todayPromise)}
          onConfirm={() => setScreen('confirm')}
          onBack={() => setScreen('home')}
        />
      )}

      {screen === 'confirm' && (
        <PromiseConfirmPage
          draft={draft}
          onEdit={() => setScreen('write')}
          onPreview={showHomePreview}
        />
      )}

      {screen === 'history' && (
        <PromiseHistoryPage
          promise={promise}
          onBack={() => setScreen('home')}
        />
      )}
    </main>
  )
}

// 기존 Vite 시작 화면 보존 (실행하지 않음)
// import { useState } from 'react'
// import heroImg from './assets/hero.png'
// import reactLogo from './assets/react.svg'
// import viteLogo from './assets/vite.svg'
// import './App.css'
//
// function App() {
//   const [count, setCount] = useState(0)
//
//   return (
//     <>
//       <section id="center">
//         <div className="hero">
//           <img src={heroImg} className="base" width="170" height="179" alt="" />
//           <img src={reactLogo} className="framework" alt="React logo" />
//           <img src={viteLogo} className="vite" alt="Vite logo" />
//         </div>
//         <div>
//           <h1>Get started</h1>
//           <p>
//             Edit <code>src/App.tsx</code> and save to test <code>HMR</code>
//           </p>
//         </div>
//         <button
//           type="button"
//           className="counter"
//           onClick={() => setCount((count) => count + 1)}
//         >
//           Count is {count}
//         </button>
//       </section>
//
//       <div className="ticks"></div>
//
//       <section id="next-steps">
//         <div id="docs">
//           <svg className="icon" role="presentation" aria-hidden="true">
//             <use href="/icons.svg#documentation-icon"></use>
//           </svg>
//           <h2>Documentation</h2>
//           <p>Your questions, answered</p>
//           <ul>
//             <li>
//               <a href="https://vite.dev/" target="_blank">
//                 <img className="logo" src={viteLogo} alt="" />
//                 Explore Vite
//               </a>
//             </li>
//             <li>
//               <a href="https://react.dev/" target="_blank">
//                 <img className="button-icon" src={reactLogo} alt="" />
//                 Learn more
//               </a>
//             </li>
//           </ul>
//         </div>
//         <div id="social">
//           <svg className="icon" role="presentation" aria-hidden="true">
//             <use href="/icons.svg#social-icon"></use>
//           </svg>
//           <h2>Connect with us</h2>
//           <p>Join the Vite community</p>
//           <ul>
//             <li>
//               <a href="https://github.com/vitejs/vite" target="_blank">
//                 <svg
//                   className="button-icon"
//                   role="presentation"
//                   aria-hidden="true"
//                 >
//                   <use href="/icons.svg#github-icon"></use>
//                 </svg>
//                 GitHub
//               </a>
//             </li>
//             <li>
//               <a href="https://chat.vite.dev/" target="_blank">
//                 <svg
//                   className="button-icon"
//                   role="presentation"
//                   aria-hidden="true"
//                 >
//                   <use href="/icons.svg#discord-icon"></use>
//                 </svg>
//                 Discord
//               </a>
//             </li>
//             <li>
//               <a href="https://x.com/vite_js" target="_blank">
//                 <svg
//                   className="button-icon"
//                   role="presentation"
//                   aria-hidden="true"
//                 >
//                   <use href="/icons.svg#x-icon"></use>
//                 </svg>
//                 X.com
//               </a>
//             </li>
//             <li>
//               <a href="https://bsky.app/profile/vite.dev" target="_blank">
//                 <svg
//                   className="button-icon"
//                   role="presentation"
//                   aria-hidden="true"
//                 >
//                   <use href="/icons.svg#bluesky-icon"></use>
//                 </svg>
//                 Bluesky
//               </a>
//             </li>
//           </ul>
//         </div>
//       </section>
//
//       <div className="ticks"></div>
//       <section id="spacer"></section>
//     </>
//   )
// }
//
// export default App
//
