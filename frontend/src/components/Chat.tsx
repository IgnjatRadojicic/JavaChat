import { useCallback, useEffect, useLayoutEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import type { User } from '../api/auth'
import { useChat, type DisplayMessage } from '../chat/useChat'
import type { Theme } from '../theme/useTheme'
import { GearLeafIcon, SendIcon } from './icons'
import { SettingsPanel } from './SettingsPanel'

const MAX_LENGTH = 2000

interface ChatProps {
  user: User
  theme: Theme
  onToggleTheme: () => void
  onSignOut: () => Promise<void>
  onAccountDeleted: () => void
  onUnauthorized: () => void
}

export function Chat({ user, theme, onToggleTheme, onSignOut, onAccountDeleted, onUnauthorized }: ChatProps) {
  // Bumped after every reply and whenever settings open, so the memory list is fresh.
  const [memoryVersion, setMemoryVersion] = useState(0)
  const refreshMemories = useCallback(() => setMemoryVersion(previous => previous + 1), [])

  const { messages, loading, sending, hasOlder, loadingOlder, error, send, loadOlder, clear } =
    useChat(onUnauthorized, refreshMemories)
  const [draft, setDraft] = useState('')
  const [settingsOpen, setSettingsOpen] = useState(false)
  const bottomRef = useRef<HTMLDivElement>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  // Follow the newest message as it grows. Older messages load at the top without
  // changing the last one, so they do not pull the view down.
  const last = messages.at(-1)
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: 'end', behavior: 'smooth' })
  }, [last?.key, last?.content])

  // Grow the text box with its content, up to a limit, like every modern chat app.
  useLayoutEffect(() => {
    const textarea = textareaRef.current
    if (!textarea) return
    textarea.style.height = 'auto'
    textarea.style.height = `${Math.min(textarea.scrollHeight, 220)}px`
  }, [draft])

  // Put the cursor back in the box when a reply finishes.
  useEffect(() => {
    if (!sending) textareaRef.current?.focus()
  }, [sending])

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

  function openSettings() {
    refreshMemories()
    setSettingsOpen(true)
  }

  const firstName = user.name?.split(' ')[0]
  const empty = !loading && messages.length === 0

  const composer = (
    <form className="composer" onSubmit={submit}>
      <label htmlFor="composer-input" className="visually-hidden">Message JavaChat</label>
      <textarea
        id="composer-input"
        ref={textareaRef}
        rows={1}
        value={draft}
        onChange={event => setDraft(event.target.value)}
        onKeyDown={onKeyDown}
        maxLength={MAX_LENGTH}
        placeholder="How can I help you today?"
        disabled={sending}
      />
      <div className="composer__footer">
        <span className="composer__hint">Enter to send, Shift + Enter for a new line</span>
        {draft.length > MAX_LENGTH - 200 && (
          <span className="composer__count">{draft.length} / {MAX_LENGTH}</span>
        )}
        <button
          type="submit"
          className="composer__send"
          disabled={sending || !draft.trim()}
          aria-label="Send message"
        >
          <SendIcon />
        </button>
      </div>
    </form>
  )

  return (
    <div className="chat-page">
      <div className={`chat ${empty ? 'chat--empty' : ''}`}>
        <header className="chat__header">
          <span className="chat__label">{empty ? 'New conversation' : 'Your conversation'}</span>
          <button
            type="button"
            className="icon-button chat__settings"
            onClick={openSettings}
            aria-label="Open settings"
            title="Settings"
          >
            <GearLeafIcon />
          </button>
        </header>

        {loading && (
          <div className="chat__loading" aria-live="polite">
            <span className="typing"><i /><i /><i /></span>
          </div>
        )}

        {empty && (
          <div className="chat__welcome">
            <h1 className="chat__greeting">
              <img className="logo-badge" src="/images/logo-192.png" alt="" width="58" height="58" />
              {firstName ? `What shall we grow today, ${firstName}?` : 'What shall we grow today?'}
            </h1>
            {error && <p className="chat__error" role="alert">{error}</p>}
            {composer}
          </div>
        )}

        {!loading && !empty && (
          <>
            <div className="chat__scroll">
              <div className="chat__messages">
                {hasOlder && (
                  <button type="button" className="pill-button chat__older" onClick={() => void loadOlder()} disabled={loadingOlder}>
                    {loadingOlder ? 'Loading…' : 'Load older messages'}
                  </button>
                )}
                {messages.map(message => (
                  <MessageView key={message.key} message={message} />
                ))}
                <div ref={bottomRef} className="chat__anchor" />
              </div>
            </div>
            <div className="chat__dock">
              {error && <p className="chat__error" role="alert">{error}</p>}
              {composer}
            </div>
          </>
        )}
      </div>

      <SettingsPanel
        open={settingsOpen}
        onClose={() => setSettingsOpen(false)}
        user={user}
        theme={theme}
        onToggleTheme={onToggleTheme}
        memoryVersion={memoryVersion}
        canClear={messages.length > 0 && !sending}
        onNewConversation={clear}
        onSignOut={onSignOut}
        onAccountDeleted={onAccountDeleted}
        onUnauthorized={onUnauthorized}
      />
    </div>
  )
}

function MessageView({ message }: { message: DisplayMessage }) {
  if (message.role === 'USER') {
    return (
      <article className="message message--user">
        <div className="message__bubble">{message.content}</div>
      </article>
    )
  }

  const waiting = message.pending && message.content === ''
  return (
    <article className="message message--assistant">
      <img className="message__avatar logo-badge" src="/images/logo-64.png" alt="" width="36" height="36" />
      <div className="message__text">
        {waiting ? (
          <span className="typing" aria-label="JavaChat is thinking"><i /><i /><i /></span>
        ) : (
          <>
            {message.content}
            {message.pending && <span className="message__caret" aria-hidden="true" />}
          </>
        )}
      </div>
    </article>
  )
}
