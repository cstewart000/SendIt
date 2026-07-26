import { useEffect, useState } from 'react'
import { api } from '../api/client'

export type DesignPart = {
  id: number
  partIndex: number
  label: string
  contourId?: string
  widthMm: number
  heightMm: number
  geometry?: { contours: { id?: string; closed?: boolean; points: { x: number; y: number }[] }[] }
}

type Props = {
  designId: number | string
  versionId: number
  selected: number[]
  onChange: (ids: number[]) => void
  onPartsLoaded?: (parts: DesignPart[]) => void
}

export function PartPicker({ designId, versionId, selected, onChange, onPartsLoaded }: Props) {
  const [parts, setParts] = useState<DesignPart[]>([])
  const [error, setError] = useState('')

  useEffect(() => {
    if (!versionId) return
    api<DesignPart[]>(`/designs/${designId}/versions/${versionId}/parts`)
      .then(list => {
        setParts(list)
        onPartsLoaded?.(list)
        if (!selected.length && list.length) onChange(list.map(p => p.id))
        console.log('[parts] loaded', list.length)
      })
      .catch(e => setError(e.message))
  }, [designId, versionId])

  function toggle(id: number) {
    onChange(selected.includes(id) ? selected.filter(x => x !== id) : [...selected, id])
  }

  return (
    <div className="grid">
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
        <button type="button" className="ghost" onClick={() => onChange(parts.map(p => p.id))}>Select all</button>
        <button type="button" className="ghost" onClick={() => onChange([])}>Clear</button>
      </div>
      {error && <p style={{ color: 'var(--danger)' }}>{error}</p>}
      {!parts.length && <p className="muted">No nestable parts yet — analyse the design first.</p>}
      {parts.map(p => (
        <label key={p.id} className="issue" style={{ display: 'flex', gap: 8, alignItems: 'center', cursor: 'pointer' }}>
          <input type="checkbox" checked={selected.includes(p.id)} onChange={() => toggle(p.id)} />
          <span><strong>{p.label}</strong> — {p.widthMm.toFixed(0)}×{p.heightMm.toFixed(0)} mm</span>
        </label>
      ))}
    </div>
  )
}
