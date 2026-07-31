import { useState } from 'react'
import type { FormEvent } from 'react'
import { api } from '../../api/client'
import { CatalogImport } from './CatalogImport'
import { emptyMachine, type Machine } from './types'

export function AdminMachines({
  items, selectedId, onSelect, onSaved,
}: {
  items: Machine[]
  selectedId: number | null
  onSelect: (id: number) => void
  onSaved: () => Promise<void>
}) {
  const [draft, setDraft] = useState<Machine>(emptyMachine())
  const [busy, setBusy] = useState(false)

  function pick(m: Machine) {
    setDraft({ ...m })
    if (m.id != null) onSelect(m.id)
    console.log('[admin] select machine', m.id, m.name)
  }

  async function save(e: FormEvent) {
    e.preventDefault()
    if (!draft.name.trim()) return
    setBusy(true)
    try {
      const saved = await api<Machine>('/admin/machines', { method: 'POST', body: JSON.stringify(draft) })
      console.log('[admin] saved machine', saved.id, saved.name)
      setDraft(emptyMachine())
      await onSaved()
      if (saved.id != null) onSelect(saved.id)
    } finally {
      setBusy(false)
    }
  }

  const set = (k: keyof Machine, v: string) =>
    setDraft(d => ({ ...d, [k]: k === 'name' || k === 'postProcessor' ? v : Number(v) }))

  return (
    <div className="panel grid">
      <h2>Machines</h2>
      <p className="muted" style={{ margin: 0 }}>Select a machine to edit its tools, operations, and pricing.</p>
      <CatalogImport mode="machine" onDone={async (id) => {
        await onSaved()
        if (id != null) onSelect(id)
      }} />
      {items.map(m => (
        <button key={m.id} type="button"
          className={m.id === selectedId ? 'issue' : 'ghost issue'}
          onClick={() => pick(m)}
          style={{
            textAlign: 'left', width: '100%',
            borderLeft: m.id === selectedId ? '3px solid var(--accent)' : undefined,
            paddingLeft: m.id === selectedId ? '0.7rem' : undefined,
          }}>
          {m.name} — {m.workXmm}×{m.workYmm}×{m.workZmm} mm · ${m.hourlyRate}/hr
        </button>
      ))}
      <form className="grid" onSubmit={save}>
        <h3>{draft.id ? 'Edit machine' : 'New machine'}</h3>
        <label>Name<input value={draft.name} onChange={e => set('name', e.target.value)} required /></label>
        <div className="row">
          <label>Work X<input type="number" value={draft.workXmm} onChange={e => set('workXmm', e.target.value)} /></label>
          <label>Work Y<input type="number" value={draft.workYmm} onChange={e => set('workYmm', e.target.value)} /></label>
          <label>Work Z<input type="number" value={draft.workZmm} onChange={e => set('workZmm', e.target.value)} /></label>
        </div>
        <div className="row">
          <label>Feed<input type="number" value={draft.defaultFeedMmMin} onChange={e => set('defaultFeedMmMin', e.target.value)} /></label>
          <label>RPM<input type="number" value={draft.defaultSpeedRpm} onChange={e => set('defaultSpeedRpm', e.target.value)} /></label>
          <label>$/hr<input type="number" step="0.01" value={draft.hourlyRate} onChange={e => set('hourlyRate', e.target.value)} /></label>
        </div>
        <label>Post-processor<input value={draft.postProcessor} onChange={e => set('postProcessor', e.target.value)} /></label>
        <div className="row">
          <button type="submit" disabled={busy}>{draft.id ? 'Update' : 'Add machine'}</button>
          {draft.id != null && (
            <button type="button" className="ghost" onClick={() => setDraft(emptyMachine())}>Clear form</button>
          )}
        </div>
      </form>
    </div>
  )
}
