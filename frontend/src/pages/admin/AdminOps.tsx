import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { api } from '../../api/client'
import { emptyOp, type Material, type ProcessDef } from './types'

export function AdminOps({
  machineId, materials, onSaved,
}: { machineId: number; materials: Material[]; onSaved: () => Promise<void> }) {
  const mat0 = materials[0]?.id ?? 0
  const [items, setItems] = useState<ProcessDef[]>([])
  const [draft, setDraft] = useState<ProcessDef>(emptyOp(machineId, mat0))
  const [busy, setBusy] = useState(false)

  async function load() {
    const list = await api<ProcessDef[]>(`/admin/machines/${machineId}/processes`)
    setItems(list)
    console.log('[admin] ops for machine', machineId, list.length)
  }

  useEffect(() => {
    setDraft(emptyOp(machineId, mat0))
    load().catch(console.error)
  }, [machineId])

  async function save(e: FormEvent) {
    e.preventDefault()
    if (!draft.name.trim() || !draft.materialId) return
    setBusy(true)
    try {
      await api('/admin/processes', {
        method: 'POST',
        body: JSON.stringify({ ...draft, machineId }),
      })
      setDraft(emptyOp(machineId, draft.materialId))
      await load()
      await onSaved()
    } finally {
      setBusy(false)
    }
  }

  const matName = (id: number) => materials.find(m => m.id === id)?.name ?? `#${id}`

  return (
    <div className="panel grid">
      <h2>Operations</h2>
      {items.map(p => (
        <button key={p.id} type="button" className="ghost issue" onClick={() => setDraft({ ...p })}
          style={{ textAlign: 'left', width: '100%' }}>
          {p.name} — {matName(p.materialId)} · {p.strategy}
        </button>
      ))}
      {!items.length && <p className="muted">No operations on this machine yet.</p>}
      <form className="grid" onSubmit={save}>
        <label>Name<input value={draft.name} onChange={e => setDraft(d => ({ ...d, name: e.target.value }))} required /></label>
        <label>Material
          <select value={draft.materialId || ''} required
            onChange={e => setDraft(d => ({ ...d, materialId: Number(e.target.value) }))}>
            <option value="" disabled>Select…</option>
            {materials.map(m => <option key={m.id} value={m.id}>{m.name}</option>)}
          </select>
        </label>
        <div className="row">
          <label>Strategy
            <select value={draft.strategy} onChange={e => setDraft(d => ({ ...d, strategy: e.target.value }))}>
              <option value="PROFILE_2_5D">Profile 2.5D</option>
              <option value="POCKET">Pocket</option>
              <option value="DRILL">Drill</option>
            </select>
          </label>
          <label>Surcharge $<input type="number" step="0.01" value={draft.surcharge}
            onChange={e => setDraft(d => ({ ...d, surcharge: Number(e.target.value) }))} /></label>
        </div>
        <div className="row">
          <button type="submit" disabled={busy || !materials.length}>
            {draft.id ? 'Update operation' : 'Add operation'}
          </button>
          {draft.id != null && (
            <button type="button" className="ghost" onClick={() => setDraft(emptyOp(machineId, mat0))}>Clear</button>
          )}
        </div>
      </form>
    </div>
  )
}
