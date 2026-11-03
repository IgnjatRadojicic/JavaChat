import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import { useChat } from '../chat/useChat'

export function Chat({ onUnauthorized }: { onUnauthorized: () => void }) {
  const { messages, loading, sending, error, send } = useChat(onUnauthorized)
  const [draft, setDraft] = useState('')
  const bottomRef = useRef<HTMLDivElement>(null)

  // Keep the newest text in view as tokens arrive.
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: 'end' })
  }, [messages])

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

  if (loading) return <p>Loading conversation…</p>

  return (
    <section>
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
