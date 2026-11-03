import { useMemories } from '../memory/useMemories'

export function MemoryPanel({ version, onUnauthorized }: { version: number; onUnauthorized: () => void }) {
  const { memories, loading, error, forget } = useMemories(version, onUnauthorized)

  return (
    <aside>
      <h2>What JavaChat remembers about you</h2>
      {loading && <p>Loading…</p>}
      {error && <p role="alert">{error}</p>}
      {!loading && memories.length === 0 && <p>Nothing yet. Tell it about yourself and it will remember.</p>}
      <ul>
        {memories.map(memory => (
          <li key={memory.id}>
            {memory.content}{' '}
            <button type="button" onClick={() => void forget(memory.id)} aria-label={`Forget: ${memory.content}`}>
              Forget
            </button>
          </li>
        ))}
      </ul>
    </aside>
  )
}
