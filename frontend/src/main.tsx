import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import LiveParticipantApp from './features/live/LiveParticipantApp'
import './dodu.css'
import './final-design.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <LiveParticipantApp />
  </StrictMode>,
)
