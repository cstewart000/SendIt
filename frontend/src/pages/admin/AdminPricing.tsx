import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { api } from '../../api/client'
import { emptyRule, type PricingRule } from './types'

export function AdminPricing({
  machineId, onSaved,
}: { machineId: number; onSaved: () => Promise<void> }) {
  const [items, setItems] = useState<PricingRule[]>([])
  const [draft, setDraft] = useState<PricingRule>(emptyRule(machineId))
  const [busy, setBusy] = useState(false)

  async function load() {
    const list = await api<PricingRule[]>(`/admin/machines/${machineId}/pricing`)
    setItems(list)
    console.log('[admin] pricing for machine', machineId, list.length)
  }

  useEffect(() => {
    setDraft(emptyRule(machineId))
    load().catch(console.error)
  }, [machineId])

  async function save(e: FormEvent) {
    e.preventDefault()
    if (!draft.ruleKey.trim()) return
    setBusy(true)
    try {
      await api('/admin/pricing', {
        method: 'POST',
        body: JSON.stringify({
          ...draft,
          machineId,
          description: draft.description || draft.name,
        }),
      })
      setDraft(emptyRule(machineId))
      await load()
      await onSaved()
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="panel grid">
      <h2>Pricing</h2>
      {items.map(r => (
        <button key={r.id} type="button" className="ghost issue" onClick={() => setDraft({ ...r })}
          style={{ textAlign: 'left', width: '100%' }}>
          {r.ruleKey}: {r.value} — {r.name}
        </button>
      ))}
      {!items.length && <p className="muted">No pricing rules on this machine yet.</p>}
      <form className="grid" onSubmit={save}>
        <label>Key<input value={draft.ruleKey} onChange={e => setDraft(d => ({ ...d, ruleKey: e.target.value }))} required /></label>
        <label>Name<input value={draft.name} onChange={e => setDraft(d => ({ ...d, name: e.target.value }))} required /></label>
        <label>Value<input type="number" step="0.01" value={draft.value}
          onChange={e => setDraft(d => ({ ...d, value: Number(e.target.value) }))} /></label>
        <div className="row">
          <button type="submit" disabled={busy}>{draft.id ? 'Update rule' : 'Add rule'}</button>
          {draft.id != null && (
            <button type="button" className="ghost" onClick={() => setDraft(emptyRule(machineId))}>Clear</button>
          )}
        </div>
      </form>
    </div>
  )
}
