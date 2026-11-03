import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError } from '../api/client'
import { fetchHistory, streamMessage, type ChatMessage, type Role } from '../api/chat'

// What the screen shows. Messages not saved yet use a local key and may be pending.
export interface DisplayMessage {
  key: string
  role: Role
  content: string
  pending?: boolean
}

function toDisplay(message: ChatMessage): DisplayMessage {
  return { key: `saved-${message.id}`, role: message.role, content: message.content }
}

function describe(error: unknown): string {
  if (error instanceof ApiError && error.status === 429) {
    const wait = error.problem.retryAfterSeconds
    return wait ? `You are sending messages too quickly. Try again in ${wait} s.` : error.message
  }
  return error instanceof Error ? error.message : 'Something went wrong.'
}

const isUnauthorized = (error: unknown) => error instanceof ApiError && error.status === 401

export function useChat(onUnauthorized: () => void) {
  const [messages, setMessages] = useState<DisplayMessage[]>([])
  const [loading, setLoading] = useState(true)
  const [sending, setSending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const abortRef = useRef<AbortController | null>(null)

  // Load the saved conversation once.
  useEffect(() => {
    let ignore = false
    fetchHistory()
      .then(history => {
        if (ignore) return
        setMessages(history.map(toDisplay))
        setLoading(false)
      })
      .catch((failure: unknown) => {
        if (ignore) return
        if (isUnauthorized(failure)) onUnauthorized()
        else {
          setError(describe(failure))
          setLoading(false)
        }
      })
    return () => {
      ignore = true
    }
  }, [onUnauthorized])

  // Leaving the chat (logout, closing) cancels a reply that is still streaming.
  useEffect(() => () => abortRef.current?.abort(), [])

  // Returns false when the message was not sent, so the input can get its text back.
  const send = useCallback(async (text: string): Promise<boolean> => {
    const userKey = `local-${crypto.randomUUID()}`
    const replyKey = `local-${crypto.randomUUID()}`
    const controller = new AbortController()
    abortRef.current = controller

    setError(null)
    setSending(true)
    setMessages(previous => [
      ...previous,
      { key: userKey, role: 'USER', content: text },
      { key: replyKey, role: 'ASSISTANT', content: '', pending: true },
    ])

    try {
      const saved = await streamMessage(
        text,
        token => setMessages(previous =>
          previous.map(m => (m.key === replyKey ? { ...m, content: m.content + token } : m))),
        controller.signal,
      )
      setMessages(previous => previous.map(m => (m.key === replyKey ? toDisplay(saved) : m)))
      return true
    } catch (failure) {
      if (controller.signal.aborted) return false
      // The backend saved nothing, so neither bubble should stay.
      setMessages(previous => previous.filter(m => m.key !== userKey && m.key !== replyKey))
      if (isUnauthorized(failure)) onUnauthorized()
      else setError(describe(failure))
      return false
    } finally {
      setSending(false)
    }
  }, [onUnauthorized])

  return { messages, loading, sending, error, send }
}
