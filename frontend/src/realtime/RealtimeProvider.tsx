import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs'
import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react'

type MessageHandler = (payload: unknown) => void
type ReconnectHandler = () => void

interface TopicRegistration {
  handlers: Set<MessageHandler>
  subscription?: StompSubscription
}

interface RealtimeContextValue {
  connected: boolean
  subscribe: (destination: string, handler: MessageHandler) => () => void
  onReconnect: (handler: ReconnectHandler) => () => void
}

const RealtimeContext = createContext<RealtimeContextValue | null>(null)

function brokerUrl() {
  const configuredUrl = import.meta.env.VITE_WS_URL?.trim()
  if (configuredUrl) return configuredUrl

  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}/ws`
}

export function RealtimeProvider({ children, token, onUnauthorized }: {
  children: ReactNode
  token: string
  onUnauthorized: () => void
}) {
  const clientRef = useRef<Client | null>(null)
  const topicsRef = useRef(new Map<string, TopicRegistration>())
  const reconnectHandlersRef = useRef(new Set<ReconnectHandler>())
  const connectedOnceRef = useRef(false)
  const [connected, setConnected] = useState(false)

  const attachTopic = useCallback((destination: string, topic: TopicRegistration) => {
    const client = clientRef.current
    if (!client?.connected || topic.subscription) return
    topic.subscription = client.subscribe(destination, (message: IMessage) => {
      try {
        const payload: unknown = JSON.parse(message.body)
        topic.handlers.forEach((handler) => handler(payload))
      } catch (error) {
        console.error(`Ignored invalid STOMP message from ${destination}`, error)
      }
    })
  }, [])

  const subscribe = useCallback((destination: string, handler: MessageHandler) => {
    let topic = topicsRef.current.get(destination)
    if (!topic) {
      topic = { handlers: new Set() }
      topicsRef.current.set(destination, topic)
    }
    topic.handlers.add(handler)
    attachTopic(destination, topic)

    return () => {
      const current = topicsRef.current.get(destination)
      if (!current) return
      current.handlers.delete(handler)
      if (current.handlers.size === 0) {
        current.subscription?.unsubscribe()
        topicsRef.current.delete(destination)
      }
    }
  }, [attachTopic])

  const onReconnect = useCallback((handler: ReconnectHandler) => {
    reconnectHandlersRef.current.add(handler)
    return () => { reconnectHandlersRef.current.delete(handler) }
  }, [])

  useEffect(() => {
    const client = new Client({
      brokerURL: brokerUrl(),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 3_000,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      onConnect: () => {
        topicsRef.current.forEach((topic, destination) => {
          topic.subscription = undefined
          attachTopic(destination, topic)
        })
        setConnected(true)
        if (connectedOnceRef.current) {
          reconnectHandlersRef.current.forEach((handler) => handler())
        }
        connectedOnceRef.current = true
      },
      onWebSocketClose: () => {
        topicsRef.current.forEach((topic) => { topic.subscription = undefined })
        setConnected(false)
      },
      onStompError: (frame) => {
        console.error('STOMP broker error', frame.headers.message)
        void client.deactivate()
        onUnauthorized()
      },
    })
    clientRef.current = client
    client.activate()

    return () => {
      clientRef.current = null
      void client.deactivate()
    }
  }, [attachTopic, onUnauthorized, token])

  return <RealtimeContext.Provider value={{ connected, subscribe, onReconnect }}>
    {children}
  </RealtimeContext.Provider>
}

export function useRealtimeSubscription<T>(
  destination: string,
  handleMessage: (payload: T) => void,
  handleReconnect?: () => void,
) {
  const context = useContext(RealtimeContext)
  if (!context) throw new Error('useRealtimeSubscription must be used within RealtimeProvider')
  const messageRef = useRef(handleMessage)
  const reconnectRef = useRef(handleReconnect)
  messageRef.current = handleMessage
  reconnectRef.current = handleReconnect

  useEffect(() => context.subscribe(destination, (payload) => messageRef.current(payload as T)),
    [context, destination])
  useEffect(() => handleReconnect
    ? context.onReconnect(() => reconnectRef.current?.())
    : undefined, [context, handleReconnect])

  return context.connected
}
