import { useCallback, useEffect, useRef, useState } from 'react'
import { clearHistory, fetchHistory, streamMessage, type ChatMessage, type Role } from '../api/chat'
import { describeError, isUnauthorized } from '../api/errors'
import { createTypewriter, type Typewriter } from './typewriter'

// The backend returns history in pages of this size. A full page means older messages may exist.
const PAGE_SIZE = 50

// What the screen shows. Saved messages carry their database id; ones not saved yet
// use a local key and may be pending.
export interface DisplayMessage {
  key: string
  id?: number
  role: Role
  content: string
  pending?: boolean
}

function toDisplay(message: ChatMessage): DisplayMessage {
  return { key: `saved-${message.id}`, id: message.id, role: message.role, content: message.content }
}

export function useChat(onUnauthorized: () => void, onReplied: () => void) {
  const [messages, setMessages] = useState<DisplayMessage[]>([])
  const [loading, setLoading] = useState(true)
  const [sending, setSending] = useState(false)
  const [hasOlder, setHasOlder] = useState(false)
  const [loadingOlder, setLoadingOlder] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const abortRef = useRef<AbortController | null>(null)
  const typewriterRef = useRef<Typewriter | null>(null)

  const fail = useCallback((failure: unknown) => {
    if (isUnauthorized(failure)) onUnauthorized()
    else setError(describeError(failure))
  }, [onUnauthorized])

  // Load the newest page of the saved conversation once.
  useEffect(() => {
    let ignore = false
    fetchHistory()
      .then(history => {
        if (ignore) return
        setMessages(history.map(toDisplay))
        setHasOlder(history.length === PAGE_SIZE)
        setLoading(false)
      })
      .catch((failure: unknown) => {
        if (ignore) return
        fail(failure)
        setLoading(false)
      })
    return () => {
      ignore = true
    }
  }, [fail])

  // Leaving the chat (logout, closing) cancels a reply that is still streaming or typing.
  useEffect(() => () => {
    abortRef.current?.abort()
    typewriterRef.current?.stop()
  }, [])

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

    // Tokens go into the typewriter, which reveals them on screen at a steady pace.
    const typewriter = createTypewriter(visible =>
      setMessages(previous => previous.map(m => (m.key === replyKey ? { ...m, content: visible } : m))))
    typewriterRef.current = typewriter

    try {
      const saved = await streamMessage(text, token => typewriter.push(token), controller.signal)
      // Let the typing finish before the bubble becomes the saved message.
      await typewriter.drain()
      // Same key as before, so React updates this bubble in place instead of replacing it.
      setMessages(previous => previous.map(m => (m.key === replyKey
        ? { ...m, id: saved.id, content: saved.content, pending: false }
        : m)))
      // The model may have saved a memory while answering.
      onReplied()
      return true
    } catch (failure) {
      typewriter.stop()
      if (controller.signal.aborted) return false
      // The backend saved nothing, so neither bubble should stay.
      setMessages(previous => previous.filter(m => m.key !== userKey && m.key !== replyKey))
      fail(failure)
      return false
    } finally {
      setSending(false)
    }
  }, [fail, onReplied])

  // Fetches the page just before the oldest saved message on screen and puts it in front.
  const loadOlder = useCallback(async () => {
    const oldestId = messages.find(m => m.id !== undefined)?.id
    if (oldestId === undefined) return

    setLoadingOlder(true)
    try {
      const page = await fetchHistory(oldestId)
      setMessages(previous => [...page.map(toDisplay), ...previous])
      setHasOlder(page.length === PAGE_SIZE)
    } catch (failure) {
      fail(failure)
    } finally {
      setLoadingOlder(false)
    }
  }, [messages, fail])

  // Starts a new conversation. The server keeps long term memories.
  const clear = useCallback(async () => {
    try {
      await clearHistory()
      setMessages([])
      setHasOlder(false)
      setError(null)
    } catch (failure) {
      fail(failure)
    }
  }, [fail])

  return { messages, loading, sending, hasOlder, loadingOlder, error, send, loadOlder, clear }
}
