import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { api } from '../../api/client'
import { CatalogImport } from './CatalogImport'
import { emptyTool, type Tool } from './types'

export function AdminTools({
  machineId, onSaved,
}: { machineId: number; onSaved: () => Promise<void> }) {
  const [items, setItems] = useState<Tool[]>([])
  const [draft, setDraft] = useState<Tool>(emptyTool(machineId))
  const [busy, setBusy] = useState(false)

  async function load() {
    const list = await api<Tool[]>(`/admin/machines/${machineId}/tools`)
    setItems(list)
    console.log('[admin] tools for machine', machineId, list.length)
  }

  useEffect(() => {
    setDraft(emptyTool(machineId))
    load().catch(console.error)
  }, [machineId])

  async function save(e: FormEvent) {
    e.preventDefault()
    if (!draft.name.trim()) return
    setBusy(true)
    try {
      await api('/admin/tools', {
        method: 'POST',
        body: JSON.stringify({ ...draft, machineId }),
      })
      setDraft(emptyTool(machineId))
      await load()
      await onSaved()
    } finally {
      setBusy(false)
    }
  }

  const setNum = (k: keyof Tool, v: string) => setDraft(d => ({ ...d, [k]: Number(v) }))

  return (
    <div className="panel grid">
      <h2>Tools</h2>
      <p className="muted" style={{ margin: 0 }}>
        Hobby CNC kit (endmills, compression, ballnose, V-bits, surfacing, drills) is seeded on first boot.
      </p>
      <CatalogImport mode="tools" machineId={machineId} onDone={async () => { await load(); await onSaved() }} />
      {items.map(t => (
        <button key={t.id} type="button" className="ghost issue" onClick={() => setDraft({ ...t })}
          style={{ textAlign: 'left', width: '100%' }}>
          {t.name} — Ø{t.diameterMm}mm · {t.fluteCount}F · max {t.maxDepthMm}mm · {t.type}
        </button>
      ))}
      {!items.length && <p className="muted">No tools on this machine yet — restart backend to seed hobby kit, or add below.</p>}
      <form className="grid" onSubmit={save}>
        <label>Name<input value={draft.name} onChange={e => setDraft(d => ({ ...d, name: e.target.value }))} required /></label>
        <div className="row">
          <label>Type
            <select value={draft.type} onChange={e => setDraft(d => ({ ...d, type: e.target.value }))}>
              <option value="ENDMILL">Endmill</option>
              <option value="BALLNOSE">Ballnose</option>
              <option value="VBIT">V-bit</option>
              <option value="SURFACING">Surfacing</option>
              <option value="DRILL">Drill</option>
            </select>
          </label>
          <label>Ø mm<input type="number" step="0.1" value={draft.diameterMm} onChange={e => setNum('diameterMm', e.target.value)} /></label>
          <label>Flutes<input type="number" value={draft.fluteCount} onChange={e => setNum('fluteCount', e.target.value)} /></label>
        </div>
        <div className="row">
          <label>Max depth<input type="number" value={draft.maxDepthMm} onChange={e => setNum('maxDepthMm', e.target.value)} /></label>
          <label>Wear $<input type="number" step="0.01" value={draft.wearCharge} onChange={e => setNum('wearCharge', e.target.value)} /></label>
        </div>
        <div className="row">
          <button type="submit" disabled={busy}>{draft.id ? 'Update tool' : 'Add tool'}</button>
          {draft.id != null && (
            <button type="button" className="ghost" onClick={() => setDraft(emptyTool(machineId))}>Clear</button>
          )}
        </div>
      </form>
    </div>
  )
}
