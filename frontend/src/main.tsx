import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import LiveParticipantApp from './features/live/LiveParticipantApp'
import AppErrorBoundary from './components/AppErrorBoundary'
import ToastHost from './components/ToastHost'
import './dodu.css'
import './final-design.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ToastHost />
    <AppErrorBoundary><LiveParticipantApp /></AppErrorBoundary>
  </StrictMode>,
)
