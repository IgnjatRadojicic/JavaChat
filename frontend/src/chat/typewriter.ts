// Reveals streamed text at a steady, readable pace, whatever rhythm the network delivers it in.
// Azure sends replies in clumps, so drawing each clump as it arrives looks jumpy and too fast.
// Text received so far is the target; a timer loop moves the visible part towards it.
//
// A timer and not requestAnimationFrame on purpose: browsers pause animation frames entirely in
// background tabs, which would freeze a reply until the user came back. Timers only slow down there,
// and since progress is measured in elapsed time, the text catches up as soon as the tab is visible.

const BASE_CHARS_PER_SECOND = 60
// When a big clump arrives, type faster so the backlog is gone within about this long.
const CATCH_UP_SECONDS = 1.2
const FRAME_MS = 16

export interface Typewriter {
  push: (text: string) => void
  // Resolves once everything pushed so far is visible.
  drain: () => Promise<void>
  stop: () => void
}

export function createTypewriter(onReveal: (visible: string) => void): Typewriter {
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  let target = ''
  let shown = 0 // fractional, so slow speeds still advance smoothly between frames
  let timer: ReturnType<typeof setTimeout> | null = null
  let lastTime = 0
  let waiting: (() => void) | null = null

  function settle() {
    if (waiting) {
      const resolve = waiting
      waiting = null
      resolve()
    }
  }

  function tick() {
    const now = performance.now()
    const seconds = lastTime ? (now - lastTime) / 1000 : FRAME_MS / 1000
    lastTime = now

    const backlog = target.length - shown
    const speed = Math.max(BASE_CHARS_PER_SECOND, backlog / CATCH_UP_SECONDS)
    shown = Math.min(target.length, shown + speed * seconds)

    let end = Math.floor(shown)
    // Never cut an emoji or other two-part character in half.
    const code = target.charCodeAt(end - 1)
    if (code >= 0xd800 && code <= 0xdbff) end += 1
    onReveal(target.slice(0, end))

    if (end < target.length) {
      timer = setTimeout(tick, FRAME_MS)
    } else {
      timer = null
      lastTime = 0
      settle()
    }
  }

  return {
    push(text) {
      target += text
      if (reduceMotion) {
        shown = target.length
        onReveal(target)
        return
      }
      if (timer === null) timer = setTimeout(tick, FRAME_MS)
    },
    drain() {
      if (timer === null) return Promise.resolve()
      return new Promise(resolve => {
        waiting = resolve
      })
    },
    stop() {
      if (timer !== null) clearTimeout(timer)
      timer = null
      settle()
    },
  }
}
