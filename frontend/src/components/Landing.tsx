import { login } from '../api/auth'
import { GoogleIcon } from './icons'

export function Landing() {
  return (
    <section className="landing">
      <div className="landing__background" aria-hidden="true" />
      <div className="landing__scrim" aria-hidden="true" />

      <div className="landing__content">
        <img className="landing__logo logo-badge" src="/images/logo-192.png" alt="" width="124" height="124" />
        <h1 className="landing__title">JavaChat</h1>
        <p className="landing__subtitle">
          An assistant that grows with you. It remembers what matters, so every conversation picks up where the last one left off.
        </p>

        <button type="button" className="google-button" onClick={login}>
          <span className="google-button__icon">
            <GoogleIcon />
          </span>
          <span>Sign in with Google</span>
        </button>

        <p className="landing__note">Only your name, email and picture are used from your Google account.</p>
      </div>
    </section>
  )
}
