import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './styles.css'
import App from './App'
import { RealtimeProvider } from './realtime/RealtimeProvider'

createRoot(document.getElementById('root')!).render(
  <StrictMode><RealtimeProvider><App /></RealtimeProvider></StrictMode>,
)
