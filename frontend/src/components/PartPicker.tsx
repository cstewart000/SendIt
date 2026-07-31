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

export type PartSelection = Record<number, number> // partId -> qty (>0 selected)

type Props = {
  designId: number | string
  versionId: number
  selection: PartSelection
  onChange: (sel: PartSelection) => void
  onPartsLoaded?: (parts: DesignPart[]) => void
}

export function PartPicker({ designId, versionId, selection, onChange, onPartsLoaded }: Props) {
  const [parts, setParts] = useState<DesignPart[]>([])
  const [bulkQty, setBulkQty] = useState(1)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!versionId) return
    api<DesignPart[]>(`/designs/${designId}/versions/${versionId}/parts`)
      .then(list => {
        setParts(list)
        onPartsLoaded?.(list)
        if (!Object.keys(selection).length && list.length) {
          const next: PartSelection = {}
          list.forEach(p => { next[p.id] = 1 })
          onChange(next)
        }
        console.log('[parts] loaded', list.length)
      })
      .catch(e => setError(e.message))
  }, [designId, versionId])

  function setQty(id: number, qty: number) {
    const q = Math.max(0, Math.floor(qty))
    const next = { ...selection }
    if (q <= 0) delete next[id]
    else next[id] = q
    onChange(next)
  }

  function selectAll(qty = 1) {
    const next: PartSelection = {}
    parts.forEach(p => { next[p.id] = Math.max(1, qty) })
    onChange(next)
  }

  function applyBulk() {
    const q = Math.max(1, bulkQty)
    const next: PartSelection = {}
    const ids = Object.keys(selection).length ? Object.keys(selection).map(Number) : parts.map(p => p.id)
    ids.forEach(id => { next[id] = q })
    onChange(next)
    console.log('[parts] bulk qty', q, 'on', ids.length)
  }

  const selectedCount = Object.keys(selection).length
  const totalQty = Object.values(selection).reduce((a, b) => a + b, 0)

  return (
    <div className="grid">
      <div className="row" style={{ flexWrap: 'wrap' }}>
        <button type="button" className="ghost" onClick={() => selectAll(1)}>Select all ×1</button>
        <button type="button" className="ghost" onClick={() => onChange({})}>Clear</button>
        <input className="qty" type="number" min={1} value={bulkQty}
          onChange={e => setBulkQty(Number(e.target.value) || 1)} title="Bulk quantity" />
        <button type="button" className="ghost" onClick={applyBulk}>
          Set ×{bulkQty}
        </button>
      </div>
      <p className="muted">{selectedCount} part type(s), {totalQty} total pieces</p>
      {error && <p style={{ color: 'var(--danger)' }}>{error}</p>}
      {!parts.length && <p className="muted">No nestable parts yet — analyse the design first.</p>}
      {parts.map(p => (
        <div key={p.id} className="issue" style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
          <input type="checkbox" checked={selection[p.id] != null}
            onChange={() => setQty(p.id, selection[p.id] != null ? 0 : 1)} />
          <span style={{ flex: 1 }}>
            <strong>{p.label}</strong> — {p.widthMm.toFixed(0)}×{p.heightMm.toFixed(0)} mm
          </span>
          <label style={{ display: 'flex', gap: 4, alignItems: 'center', margin: 0 }}>
            Qty
            <input className="qty" type="number" min={0}
              value={selection[p.id] ?? 0}
              onChange={e => setQty(p.id, Number(e.target.value) || 0)} />
          </label>
        </div>
      ))}
    </div>
  )
}
