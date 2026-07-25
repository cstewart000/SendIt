import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import { Shell } from '../components/Shell'

type Design = { id: number; name: string; latestVersion: number; updatedAt: string }

export function DesignsPage() {
  const [items, setItems] = useState<Design[]>([])
  const [file, setFile] = useState<File | null>(null)
  const [name, setName] = useState('')
  const [error, setError] = useState('')

  async function load() {
    setItems(await api<Design[]>('/designs'))
  }

  useEffect(() => { load().catch(e => setError(String(e.message))) }, [])

  async function upload(e: FormEvent) {
    e.preventDefault()
    if (!file) return
    const fd = new FormData()
    fd.append('file', file)
    if (name) fd.append('name', name)
    const created = await api<{ id: number }>('/designs', { method: 'POST', body: fd })
    console.log('[designs] uploaded', created.id)
    window.location.href = `/designs/${created.id}`
  }

  return (
    <Shell>
      <div className="grid two">
        <div className="panel">
          <h2>Your designs</h2>
          {error && <p style={{ color: 'var(--danger)' }}>{error}</p>}
          <div className="grid">
            {items.map(d => (
              <Link key={d.id} to={`/designs/${d.id}`} className="issue">
                <strong>{d.name}</strong>
                <div className="muted">v{d.latestVersion}</div>
              </Link>
            ))}
            {!items.length && <p className="muted">No designs yet — upload a DXF to begin.</p>}
          </div>
        </div>
        <form className="panel grid" onSubmit={upload}>
          <h2>Upload DXF / DWG</h2>
          <label>Name<input value={name} onChange={e => setName(e.target.value)} placeholder="Optional" /></label>
          <label>File<input type="file" accept=".dxf,.dwg" onChange={e => setFile(e.target.files?.[0] || null)} /></label>
          <button type="submit" disabled={!file}>Start analysis</button>
        </form>
      </div>
    </Shell>
  )
}
