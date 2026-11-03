import type { Theme } from '../theme/useTheme'
import { MoonSproutIcon, PageLeafIcon, ShieldLeafIcon, SunflowerIcon } from './icons'

export type InfoKind = 'privacy' | 'terms'

interface TopNavProps {
  // 'media' sits on the landing photo with light text; 'solid' is the frosted bar over the chat.
  variant: 'media' | 'solid'
  theme: Theme
  onToggleTheme: () => void
  onOpenInfo: (kind: InfoKind) => void
}

export function TopNav({ variant, theme, onToggleTheme, onOpenInfo }: TopNavProps) {
  const nextTheme = theme === 'dark' ? 'light' : 'dark'

  return (
    <nav className={`top-nav top-nav--${variant}`}>
      <a className="top-nav__brand" href="/">
        <img className="logo-badge" src="/images/logo-64.png" alt="" width="46" height="46" />
        <span>JavaChat</span>
      </a>

      <div className="top-nav__actions">
        <button
          type="button"
          className="icon-button top-nav__button"
          onClick={onToggleTheme}
          aria-label={`Switch to ${nextTheme} mode`}
          title={`Switch to ${nextTheme} mode`}
        >
          {/* The key makes React swap the element, which replays the turn-in animation. */}
          <span key={theme} className="top-nav__theme-icon">
            {theme === 'dark' ? <SunflowerIcon /> : <MoonSproutIcon />}
          </span>
        </button>
        <button
          type="button"
          className="icon-button top-nav__button"
          onClick={() => onOpenInfo('privacy')}
          aria-label="Privacy"
          title="Privacy"
        >
          <ShieldLeafIcon />
        </button>
        <button
          type="button"
          className="icon-button top-nav__button"
          onClick={() => onOpenInfo('terms')}
          aria-label="Terms"
          title="Terms"
        >
          <PageLeafIcon />
        </button>
      </div>
    </nav>
  )
}
