import { useEffect, useRef, useState } from 'react'
import { deleteAccount, type User } from '../api/auth'
import { describeError } from '../api/errors'
import { useMemories } from '../memory/useMemories'
import type { Theme } from '../theme/useTheme'
import { CloseIcon, LeafIcon, LogoutIcon, SproutIcon, TrashIcon } from './icons'

interface SettingsPanelProps {
  open: boolean
  onClose: () => void
  user: User
  theme: Theme
  onToggleTheme: () => void
  memoryVersion: number
  canClear: boolean
  onNewConversation: () => Promise<void>
  onSignOut: () => Promise<void>
  onAccountDeleted: () => void
  onUnauthorized: () => void
}

export function SettingsPanel(props: SettingsPanelProps) {
  const { open, onClose, user, theme, onToggleTheme, memoryVersion, canClear } = props
  const { memories, loading, error: memoryError, forget } = useMemories(memoryVersion, props.onUnauthorized)
  const [error, setError] = useState<string | null>(null)
  const closeRef = useRef<HTMLButtonElement>(null)

  // Focus inside the panel when it opens, and close it with Escape.
  useEffect(() => {
    if (!open) return
    closeRef.current?.focus()
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onClose])

  async function newConversation() {
    if (!window.confirm('Start a new conversation? JavaChat keeps what it remembers about you.')) return
    await props.onNewConversation()
    onClose()
  }

  async function signOut() {
    try {
      await props.onSignOut()
    } catch (failure) {
      setError(describeError(failure))
    }
  }

  async function removeAccount() {
    const confirmed = window.confirm(
      'Delete your account, your conversation and everything JavaChat remembers about you? This cannot be undone.')
    if (!confirmed) return
    try {
      await deleteAccount()
      props.onAccountDeleted()
    } catch (failure) {
      setError(describeError(failure))
    }
  }

  const initials = (user.name ?? user.email).slice(0, 1).toUpperCase()

  return (
    <div className={`settings ${open ? 'settings--open' : ''}`} inert={!open}>
      <div className="settings__backdrop" onClick={onClose} />

      <aside className="settings__panel" role="dialog" aria-modal="true" aria-labelledby="settings-title">
        <header className="settings__header">
          <h2 id="settings-title">Settings</h2>
          <button ref={closeRef} type="button" className="icon-button" onClick={onClose} aria-label="Close settings">
            <CloseIcon />
          </button>
        </header>

        <section className="settings__card settings__profile">
          {user.pictureUrl ? (
            <img src={user.pictureUrl} alt="" width="52" height="52" referrerPolicy="no-referrer" />
          ) : (
            <span className="settings__initials">{initials}</span>
          )}
          <div>
            <p className="settings__name">{user.name ?? 'Signed in'}</p>
            <p className="settings__email">{user.email}</p>
          </div>
        </section>

        <section className="settings__card">
          <div className="settings__row">
            <div>
              <h3>Dark mode</h3>
              <p className="settings__hint">The forest at night.</p>
            </div>
            <button
              type="button"
              role="switch"
              aria-checked={theme === 'dark'}
              aria-label="Dark mode"
              className={`switch ${theme === 'dark' ? 'switch--on' : ''}`}
              onClick={onToggleTheme}
            >
              <span className="switch__thumb" />
            </button>
          </div>
        </section>

        <section className="settings__card">
          <h3>What JavaChat remembers</h3>
          <p className="settings__hint">Facts it saved to remember you by. Forget any of them at any time.</p>
          {loading && <p className="settings__hint">Loading…</p>}
          {memoryError && <p className="settings__error" role="alert">{memoryError}</p>}
          {!loading && memories.length === 0 && (
            <p className="settings__empty">Nothing yet. Tell it about yourself and it will remember.</p>
          )}
          <ul className="memory-list">
            {memories.map(memory => (
              <li key={memory.id} className="memory-list__item">
                <LeafIcon className="memory-list__leaf" />
                <span>{memory.content}</span>
                <button
                  type="button"
                  className="icon-button memory-list__forget"
                  onClick={() => void forget(memory.id)}
                  aria-label={`Forget: ${memory.content}`}
                  title="Forget"
                >
                  <TrashIcon />
                </button>
              </li>
            ))}
          </ul>
        </section>

        <section className="settings__card settings__actions">
          <button type="button" className="pill-button" onClick={() => void newConversation()} disabled={!canClear}>
            <SproutIcon /> New conversation
          </button>
          <button type="button" className="pill-button" onClick={() => void signOut()}>
            <LogoutIcon /> Log out
          </button>
          <button type="button" className="pill-button danger" onClick={() => void removeAccount()}>
            <TrashIcon /> Delete account
          </button>
          {error && <p className="settings__error" role="alert">{error}</p>}
        </section>
      </aside>
    </div>
  )
}
