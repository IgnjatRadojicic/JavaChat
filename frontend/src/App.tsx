import { useCallback, useState } from 'react'
import { useAuth } from './auth/useAuth'
import { Chat } from './components/Chat'
import { InfoDialog } from './components/InfoDialog'
import { Landing } from './components/Landing'
import { TopNav, type InfoKind } from './components/TopNav'
import { useTheme } from './theme/useTheme'

function App() {
  const { theme, toggle: toggleTheme } = useTheme()
  const { state, retry, signOut, markSignedOut } = useAuth()
  const [info, setInfo] = useState<InfoKind | null>(null)
  const closeInfo = useCallback(() => setInfo(null), [])

  let page
  switch (state.status) {
    case 'loading':
      page = (
        <div className="splash" aria-live="polite">
          <img src="/images/logo-192.png" alt="" />
          <span className="splash__brand">JAVACHAT</span>
          <div className="splash__line" />
        </div>
      )
      break

    case 'error':
      page = (
        <div className="splash">
          <div className="splash__error" role="alert">
            <img src="/images/logo-192.png" alt="" />
            <p>Could not reach JavaChat. {state.message}</p>
            <button type="button" className="pill-button" onClick={retry}>Try again</button>
          </div>
        </div>
      )
      break

    case 'signedOut':
      page = <Landing />
      break

    case 'signedIn':
      page = (
        <Chat
          user={state.user}
          theme={theme}
          onToggleTheme={toggleTheme}
          onSignOut={signOut}
          onAccountDeleted={markSignedOut}
          onUnauthorized={markSignedOut}
        />
      )
      break
  }

  return (
    <>
      <TopNav
        variant={state.status === 'signedOut' ? 'media' : 'solid'}
        theme={theme}
        onToggleTheme={toggleTheme}
        onOpenInfo={setInfo}
      />
      {page}
      {info && <InfoDialog kind={info} onClose={closeInfo} />}
    </>
  )
}

export default App
