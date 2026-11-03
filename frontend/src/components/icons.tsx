import type { SVGProps } from 'react'

// One family of minimal, plant themed line icons on a 24px grid. They inherit color from the text.
type IconProps = SVGProps<SVGSVGElement>

function Icon({ children, ...props }: IconProps) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.8}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
      {...props}
    >
      {children}
    </svg>
  )
}

/** Light theme: a sunflower. */
export function SunflowerIcon(props: IconProps) {
  return (
    <Icon {...props}>
      <circle cx="12" cy="12" r="3.2" />
      {[0, 45, 90, 135, 180, 225, 270, 315].map(angle => (
        <path key={angle} d="M12 3.2c1.1 1 1.1 2.6 0 3.6c-1.1-1-1.1-2.6 0-3.6z" transform={`rotate(${angle} 12 12)`} />
      ))}
    </Icon>
  )
}

/** Dark theme: a crescent moon with a leaf sprouting from it. */
export function MoonSproutIcon(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M19.5 14.6A8 8 0 1 1 9.4 4.5a6.4 6.4 0 0 0 10.1 10.1z" />
      <path d="M16.5 3.5c0 1.9 1.3 3.1 3.3 3.1c0-1.9-1.4-3.1-3.3-3.1z" />
    </Icon>
  )
}

/** Privacy: a shield holding a leaf. */
export function ShieldLeafIcon(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M12 3l7 2.8v5.6c0 4.3-2.9 8.1-7 9.6c-4.1-1.5-7-5.3-7-9.6V5.8L12 3z" />
      <path d="M9 15.2c0-3.2 2-5.2 6-5.2c0 3.8-2 5.6-5.2 5.6" />
      <path d="M9 15.6l2.8-2.8" />
    </Icon>
  )
}

/** Terms: a page with a leaf on it. */
export function PageLeafIcon(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M7 3h7l5 5v11a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z" />
      <path d="M14 3v5h5" />
      <path d="M9 17.6c0-2.4 1.6-4.1 4.8-4.1c0 3.1-1.7 4.6-4.1 4.6" />
      <path d="M9 18l2.5-2.5" />
    </Icon>
  )
}

/** Settings: a cog with a leaf in the middle. */
export function GearLeafIcon(props: IconProps) {
  return (
    <Icon {...props}>
      <circle cx="12" cy="12" r="6.2" />
      {[0, 45, 90, 135, 180, 225, 270, 315].map(angle => (
        <path key={angle} d="M12 2.6v2.6" strokeWidth={2.6} transform={`rotate(${angle} 12 12)`} />
      ))}
      <path d="M10.2 14.2c0-2.2 1.3-3.6 3.9-3.6c0 2.6-1.4 3.9-3.4 3.9" />
    </Icon>
  )
}

/** Send: an arrow that grows upward. */
export function SendIcon(props: IconProps) {
  return (
    <Icon strokeWidth={2.2} {...props}>
      <path d="M12 19V5.5" />
      <path d="M6.5 11L12 5.5L17.5 11" />
    </Icon>
  )
}

/** New conversation: a fresh sprout. */
export function SproutIcon(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M12 21v-9" />
      <path d="M12 12c0-3.6-2.6-6.2-6.8-6.2c0 4.1 2.6 6.2 6.8 6.2z" />
      <path d="M12 10.4c0-3.1 2.3-5.2 5.8-5.2c0 3.4-2.3 5.2-5.8 5.2z" />
    </Icon>
  )
}

/** A single leaf, used as the bullet for memories. */
export function LeafIcon(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M5 19c0-8 5-13 14-13c0 9-5 13-12.5 13" />
      <path d="M5 19l7-7" />
    </Icon>
  )
}

export function CloseIcon(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M6 6l12 12M18 6L6 18" />
    </Icon>
  )
}

export function LogoutIcon(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M14 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3" />
      <path d="M10 16l-4-4l4-4" />
      <path d="M6 12h9" />
    </Icon>
  )
}

export function TrashIcon(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M4 7h16" />
      <path d="M9 7V4.8A.8.8 0 0 1 9.8 4h4.4a.8.8 0 0 1 .8.8V7" />
      <path d="M6.5 7l.9 12.2A2 2 0 0 0 9.4 21h5.2a2 2 0 0 0 2-1.8L17.5 7" />
    </Icon>
  )
}

/** Google's multicolour "G", as required on Google sign-in buttons. */
export function GoogleIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 48 48" aria-hidden="true" focusable="false" {...props}>
      <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0C14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z" />
      <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z" />
      <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z" />
      <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3c-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z" />
    </svg>
  )
}
