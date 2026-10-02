import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './styles.css'
import { AuthenticationRoot } from './auth/AuthenticationRoot'

createRoot(document.getElementById('root')!).render(
  <StrictMode><AuthenticationRoot /></StrictMode>,
)
