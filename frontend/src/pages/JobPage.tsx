import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { api, apiBlob } from '../api/client'
import { Shell } from '../components/Shell'
import { PartPicker, type PartSelection } from '../components/PartPicker'
import { Viewer2D } from '../components/viewer2d/Viewer2D'

type JobPart = {
  id: number; label: string; quantity: number; widthMm: number; heightMm: number
  designVersionId: number; designPartId?: number
}
type Job = {
  id: number; status: string; nestingLocked: boolean; hasGcode: boolean; parts?: JobPart[]
  nesting?: {
    sheetWidth: number; sheetHeight: number; sheetCount: number
    placements: { x: number; y: number; width: number; height: number; label?: string; sheetIndex?: number }[]
  }
  quote?: { total: number; currency: string; cycleMinutes: number; lines: { label: string; amount: number }[] }
}
type DesignSum = { id: number; name: string; latestVersion: number; partCount?: number }
type Detail = { id: number; name: string; versions: { id: number; versionNumber: number }[] }

export function JobPage() {
  const { id } = useParams()
  const [job, setJob] = useState<Job | null>(null)
  const [designs, setDesigns] = useState<DesignSum[]>([])
  const [designId, setDesignId] = useState<number | ''>('')
  const [versionId, setVersionId] = useState<number | ''>('')
  const [selection, setSelection] = useState<PartSelection>({})
  const [allQty, setAllQty] = useState(1)
  const [error, setError] = useState('')

  async function reload() {
    setJob(await api<Job>(`/jobs/${id}`))
  }

  useEffect(() => {
    reload().catch(e => setError(e.message))
    api<DesignSum[]>('/designs').then(setDesigns).catch(console.error)
  }, [id])

  async function pickDesign(did: number) {
    setDesignId(did)
    setSelection({})
    const d = await api<Detail>(`/designs/${did}`)
    setVersionId(d.versions[0]?.id || '')
  }

  async function addParts() {
    const quantities = Object.entries(selection).map(([partId, quantity]) => ({
      partId: Number(partId), quantity,
    }))
    if (!versionId || !quantities.length) return
    setJob(await api<Job>(`/jobs/${id}/parts`, {
      method: 'POST',
      body: JSON.stringify({ designVersionId: versionId, quantities }),
    }))
    console.log('[job] added', quantities)
  }

  async function setJobPartQty(jobPartId: number, quantity: number) {
    setJob(await api<Job>(`/jobs/${id}/parts`, {
      method: 'PATCH',
      body: JSON.stringify({ updates: [{ jobPartId, quantity: Math.max(1, quantity) }] }),
    }))
  }

  async function setAllJobQty() {
    setJob(await api<Job>(`/jobs/${id}/parts`, {
      method: 'PATCH',
      body: JSON.stringify({ allQuantity: Math.max(1, allQty) }),
    }))
  }

  async function nest() { setJob(await api<Job>(`/jobs/${id}/nest`, { method: 'POST' })) }
  async function lock() { setJob(await api<Job>(`/jobs/${id}/lock-nesting`, { method: 'POST' })) }
  async function quote() { setJob(await api<Job>(`/jobs/${id}/quote`, { method: 'POST' })) }
  async function approve() { setJob(await api<Job>(`/jobs/${id}/approve`, { method: 'POST' })) }

  async function download(kind: 'gcode' | 'setup-sheet') {
    const blob = await apiBlob(`/jobs/${id}/${kind}`)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = kind === 'gcode' ? `job-${id}.ngc` : `job-${id}-setup.txt`
    a.click()
  }

  const addTotal = Object.values(selection).reduce((a, b) => a + b, 0)

  return (
    <Shell>
      {error && <p style={{ color: 'var(--danger)' }}>{error}</p>}
      <div className="grid two">
        <div className="panel">
          <h2>Job #{job?.id} — {job?.status}</h2>
          <Viewer2D
            nests={job?.nesting?.placements}
            sheet={job?.nesting ? { width: job.nesting.sheetWidth, height: job.nesting.sheetHeight } : undefined}
          />
          <p className="muted">Sheets: {job?.nesting?.sheetCount ?? 0}</p>
          <h3>Parts on job</h3>
          {!job?.nestingLocked && (job?.parts?.length ?? 0) > 0 && (
            <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 8 }}>
              <input type="number" min={1} value={allQty} style={{ width: 72 }}
                onChange={e => setAllQty(Number(e.target.value) || 1)} />
              <button type="button" className="ghost" onClick={setAllJobQty}>
                Set all parts to ×{allQty}
              </button>
            </div>
          )}
          {(job?.parts || []).map(p => (
            <div key={p.id} className="issue" style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
              <span style={{ flex: 1 }}>
                {p.label} — {p.widthMm.toFixed(0)}×{p.heightMm.toFixed(0)} mm
              </span>
              {job?.nestingLocked ? (
                <span>×{p.quantity}</span>
              ) : (
                <input type="number" min={1} style={{ width: 64 }} defaultValue={p.quantity}
                  key={`${p.id}-${p.quantity}`}
                  onBlur={e => setJobPartQty(p.id, Number(e.target.value) || 1)} />
              )}
            </div>
          ))}
          {!job?.parts?.length && <p className="muted">No parts yet — add from designs below.</p>}
        </div>
        <div className="panel grid">
          {!job?.nestingLocked && (
            <>
              <h3>Add from designs</h3>
              <label>Design
                <select value={designId} onChange={e => pickDesign(Number(e.target.value))}>
                  <option value="">Select…</option>
                  {designs.map(d => (
                    <option key={d.id} value={d.id}>
                      {d.name}{d.partCount != null ? ` (${d.partCount} parts)` : ''}
                    </option>
                  ))}
                </select>
              </label>
              {designId && versionId && (
                <PartPicker designId={designId} versionId={Number(versionId)}
                  selection={selection} onChange={setSelection} />
              )}
              <button className="ghost" onClick={addParts} disabled={!addTotal}>
                Add to job ({Object.keys(selection).length} types, {addTotal} pieces)
              </button>
            </>
          )}
          <button onClick={nest} disabled={!job?.parts?.length}>Auto-nest</button>
          <button className="ghost" onClick={lock} disabled={!job?.nesting?.placements?.length}>Lock nesting</button>
          <button className="ghost" onClick={quote} disabled={!job?.nestingLocked}>Generate quote</button>
          {job?.quote && (
            <div>
              <h3>Quote {job.quote.currency} {job.quote.total}</h3>
              {job.quote.lines.map(l => (
                <div key={l.label} className="issue">{l.label}: {l.amount}</div>
              ))}
            </div>
          )}
          <button onClick={approve} disabled={!job?.quote}>Approve order</button>
          {job?.hasGcode && (
            <>
              <button className="ghost" onClick={() => download('gcode')}>Download G-code</button>
              <button className="ghost" onClick={() => download('setup-sheet')}>Download setup sheet</button>
            </>
          )}
        </div>
      </div>
    </Shell>
  )
}
