import { Link } from 'react-router-dom'
import type { JobSummaryView } from './jobTypes'

function fmt(v: number | null | undefined, suffix = '') {
  return v == null ? '—' : `${v}${suffix}`
}

function areaLabel(mm2: number) {
  if (!mm2) return '—'
  if (mm2 >= 1_000_000) return `${(mm2 / 1_000_000).toFixed(2)} m²`
  return `${Math.round(mm2).toLocaleString()} mm²`
}

export function JobCard({ job }: { job: JobSummaryView }) {
  const s = job.summary
  return (
    <Link to={`/jobs/${job.id}`} className="job-card">
      <div className="job-card-head">
        <strong>{job.title || `Job #${job.id}`}</strong>
        <span className="job-status">{job.status}</span>
      </div>
      <div className="job-meta">
        <div><span className="muted">Sheets</span><b>{s?.sheetCount ?? 0}</b></div>
        <div><span className="muted">Parts</span><b>{s?.partCount ?? 0}</b></div>
        <div><span className="muted">Area</span><b>{areaLabel(s?.partsAreaMm2 ?? 0)}</b></div>
        <div><span className="muted">Time</span><b>{fmt(s?.cycleMinutes, ' min')}</b></div>
        <div><span className="muted">Cost</span>
          <b>{s?.cost == null ? '—' : `${s.currency || 'AUD'} ${s.cost.toFixed(2)}`}</b>
        </div>
      </div>
      {job.hasGcode && <p className="muted" style={{ margin: 0 }}>G-code ready</p>}
    </Link>
  )
}
