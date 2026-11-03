import { useEffect, useRef } from 'react'
import { CloseIcon, PageLeafIcon, ShieldLeafIcon } from './icons'
import type { InfoKind } from './TopNav'

const CONTENT: Record<InfoKind, { title: string; paragraphs: string[] }> = {
  privacy: {
    title: 'Privacy',
    paragraphs: [
      'JavaChat signs you in with Google and only keeps your name, email address and profile picture from your Google account.',
      'Your messages and a short summary of older parts of your conversation are stored so the assistant can follow along. Facts the assistant decides to remember about you are stored separately, and you can see and delete each one under Settings.',
      'Replies are written by an OpenAI model hosted on Microsoft Azure in the EU. Your messages are sent there to produce each answer and are not used to train the model.',
      'You can start a new conversation at any time, and deleting your account removes your messages, summary and memories for good.',
    ],
  },
  terms: {
    title: 'Terms',
    paragraphs: [
      'JavaChat is a personal portfolio project offered as is, without guarantees about availability or the accuracy of its answers.',
      'Answers come from an AI model and can be wrong. Do not rely on them for medical, legal or financial decisions.',
      'Please do not share passwords, payment details or other sensitive information in the chat.',
      'To keep things fair and affordable, each person can send up to 10 messages a minute and 200 a day.',
    ],
  },
}

export function InfoDialog({ kind, onClose }: { kind: InfoKind; onClose: () => void }) {
  const closeRef = useRef<HTMLButtonElement>(null)
  const { title, paragraphs } = CONTENT[kind]

  // Focus the close button when the dialog opens, and close on Escape.
  useEffect(() => {
    closeRef.current?.focus()
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <div className="dialog-backdrop" onClick={onClose}>
      <div
        className="dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="info-dialog-title"
        onClick={event => event.stopPropagation()}
      >
        <header className="dialog__header">
          <span className="dialog__badge">{kind === 'privacy' ? <ShieldLeafIcon /> : <PageLeafIcon />}</span>
          <h2 id="info-dialog-title">{title}</h2>
          <button ref={closeRef} type="button" className="icon-button" onClick={onClose} aria-label="Close">
            <CloseIcon />
          </button>
        </header>
        <div className="dialog__body">
          {paragraphs.map(text => (
            <p key={text}>{text}</p>
          ))}
        </div>
      </div>
    </div>
  )
}
