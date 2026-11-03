import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import { useChat } from '../chat/useChat'

interface ChatProps {
  onUnauthorized: () => void
  onReplied: () => void
}

export function Chat({ onUnauthorized, onReplied }: ChatProps) {
  const { messages, loading, sending, hasOlder, loadingOlder, error, send, loadOlder, clear } =
    useChat(onUnauthorized, onReplied)
  const [draft, setDraft] = useState('')
  const bottomRef = useRef<HTMLDivElement>(null)

  // Follow the newest message as it grows. Loading older messages adds to the top
  // without changing the last one, so it does not yank the view to the bottom.
  const last = messages.at(-1)
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: 'end' })
  }, [last?.key, last?.content])

  async function submit(event: FormEvent) {
    event.preventDefault()
    const text = draft.trim()
    if (!text || sending) return
    setDraft('')
    const sent = await send(text)
    if (!sent) setDraft(text)
  }

  // Enter sends, Shift+Enter makes a new line.
  function onKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      event.currentTarget.form?.requestSubmit()
    }
  }

  function startNewConversation() {
    if (window.confirm('Start a new conversation? JavaChat keeps what it remembers about you.')) {
      void clear()
    }
  }

  if (loading) return <p>Loading conversation…</p>

  return (
    <section>
      <button type="button" onClick={startNewConversation} disabled={sending || messages.length === 0}>
        New conversation
      </button>

      {hasOlder && (
        <button type="button" onClick={() => void loadOlder()} disabled={loadingOlder}>
          {loadingOlder ? 'Loading…' : 'Load older messages'}
        </button>
      )}

      <ol>
        {messages.map(message => (
          <li key={message.key} style={{ whiteSpace: 'pre-wrap' }}>
            <strong>{message.role === 'USER' ? 'You' : 'JavaChat'}: </strong>
            {message.pending && message.content === '' ? '…' : message.content}
          </li>
        ))}
      </ol>
      <div ref={bottomRef} />

      {error && <p role="alert">{error}</p>}

      <form onSubmit={submit}>
        <textarea
          value={draft}
          onChange={event => setDraft(event.target.value)}
          onKeyDown={onKeyDown}
          maxLength={2000}
          placeholder="Message JavaChat"
          disabled={sending}
        />
        <button type="submit" disabled={sending || !draft.trim()}>Send</button>
      </form>
    </section>
  )
}
