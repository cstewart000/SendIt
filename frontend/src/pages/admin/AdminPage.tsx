import { useEffect, useState } from 'react'
import { api } from '../../api/client'
import { Shell } from '../../components/Shell'
import { AdminMachines } from './AdminMachines'
import { AdminTools } from './AdminTools'
import { AdminOps } from './AdminOps'
import { AdminPricing } from './AdminPricing'
import type { Machine, Material } from './types'

type Job = { id: number; title?: string; status: string }

export function AdminPage() {
  const [machines, setMachines] = useState<Machine[]>([])
  const [materials, setMaterials] = useState<Material[]>([])
  const [jobs, setJobs] = useState<Job[]>([])
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [msg, setMsg] = useState('')

  async function load() {
    const [m, mat, j] = await Promise.all([
      api<Machine[]>('/admin/machines'),
      api<Material[]>('/admin/materials'),
      api<Job[]>('/admin/jobs'),
    ])
    setMachines(m)
    setMaterials(mat)
    setJobs(j)
    if (selectedId == null && m[0]?.id != null) setSelectedId(m[0].id!)
    console.log('[admin] machines=', m.length, 'selected=', selectedId)
  }

  useEffect(() => { load().catch(e => setMsg(e.message)) }, [])

  async function setStatus(id: number, status: string) {
    await api(`/admin/jobs/${id}/status`, { method: 'PATCH', body: JSON.stringify({ status }) })
    await load()
  }

  const selected = machines.find(m => m.id === selectedId)

  return (
    <Shell>
      <div className="grid" style={{ gap: '1.25rem' }}>
        <div className="grid two">
          <AdminMachines
            items={machines}
            selectedId={selectedId}
            onSelect={setSelectedId}
            onSaved={load}
          />
          <div className="panel">
            <h2>Production queue</h2>
            {jobs.map(j => (
              <div key={j.id} className="issue" style={{ display: 'flex', justifyContent: 'space-between', gap: 8 }}>
                <span>{j.title || `Job #${j.id}`} — {j.status}</span>
                <button className="ghost" onClick={() => setStatus(j.id, 'IN_PRODUCTION')}>In production</button>
              </div>
            ))}
            {!jobs.length && <p className="muted">Queue empty.</p>}
            {msg && <p className="muted">{msg}</p>}
          </div>
        </div>
        {selectedId != null && selected ? (
          <>
            <h2 style={{ margin: 0 }}>Children of {selected.name}</h2>
            <div className="grid two">
              <AdminTools machineId={selectedId} onSaved={load} />
              <AdminPricing machineId={selectedId} onSaved={load} />
            </div>
            <AdminOps machineId={selectedId} materials={materials} onSaved={load} />
          </>
        ) : (
          <p className="muted">Select or create a machine to manage tools, pricing, and operations.</p>
        )}
      </div>
    </Shell>
  )
}
