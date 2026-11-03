import { useCallback, useEffect, useState } from 'react'
import { deleteMemory, fetchMemories, type Memory } from '../api/memories'
import { describeError, isNotFound, isUnauthorized } from '../api/errors'

// `version` changes after every reply, so the list reloads when the model may have saved something.
export function useMemories(version: number, onUnauthorized: () => void) {
  const [memories, setMemories] = useState<Memory[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let ignore = false
    fetchMemories()
      .then(list => {
        if (ignore) return
        setMemories(list)
        setError(null)
        setLoading(false)
      })
      .catch((failure: unknown) => {
        if (ignore) return
        if (isUnauthorized(failure)) onUnauthorized()
        else setError(describeError(failure))
        setLoading(false)
      })
    return () => {
      ignore = true
    }
  }, [version, onUnauthorized])

  const forget = useCallback(async (id: string) => {
    try {
      await deleteMemory(id)
      setMemories(previous => previous.filter(m => m.id !== id))
    } catch (failure) {
      // Already gone on the server (deleted in another tab): just drop it from the list.
      if (isNotFound(failure)) setMemories(previous => previous.filter(m => m.id !== id))
      else if (isUnauthorized(failure)) onUnauthorized()
      else setError(describeError(failure))
    }
  }, [onUnauthorized])

  return { memories, loading, error, forget }
}
