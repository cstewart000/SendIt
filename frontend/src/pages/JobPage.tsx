import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { api, apiBlob } from '../api/client'
import { Shell } from '../components/Shell'
import { Viewer2D } from '../components/viewer2d/Viewer2D'

type Job = {
  id: number; status: string; nestingLocked: boolean; hasGcode: boolean
  nesting?: {
    sheetWidth: number; sheetHeight: number; sheetCount: number
    placements: { x: number; y: number; width: number; height: number; label?: string; sheetIndex?: number }[]
  }
  quote?: { total: number; currency: string; cycleMinutes: number; lines: { label: string; amount: number }[] }
}

export function JobPage() {
  const { id } = useParams()
  const [job, setJob] = useState<Job | null>(null)
  const [error, setError] = useState('')

  async function reload() {
    setJob(await api<Job>(`/jobs/${id}`))
  }

  useEffect(() => { reload().catch(e => setError(e.message)) }, [id])

  async function nest() {
    setJob(await api<Job>(`/jobs/${id}/nest`, { method: 'POST' }))
  }
  async function lock() {
    setJob(await api<Job>(`/jobs/${id}/lock-nesting`, { method: 'POST' }))
  }
  async function quote() {
    setJob(await api<Job>(`/jobs/${id}/quote`, { method: 'POST' }))
  }
  async function approve() {
    setJob(await api<Job>(`/jobs/${id}/approve`, { method: 'POST' }))
  }
  async function download(kind: 'gcode' | 'setup-sheet') {
    const blob = await apiBlob(`/jobs/${id}/${kind}`)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = kind === 'gcode' ? `job-${id}.ngc` : `job-${id}-setup.txt`
    a.click()
    console.log('[job] downloaded', kind)
  }

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
        </div>
        <div className="panel grid">
          <button onClick={nest}>Auto-nest</button>
          <button className="ghost" onClick={lock} disabled={!job?.nesting?.placements?.length}>Lock nesting</button>
          <button className="ghost" onClick={quote} disabled={!job?.nestingLocked}>Generate quote</button>
          {job?.quote && (
            <div>
              <h3>Quote {job.quote.currency} {job.quote.total}</h3>
              <p className="muted">Cycle ~ {job.quote.cycleMinutes} min</p>
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
