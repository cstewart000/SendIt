import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api/client'
import { Shell } from '../components/Shell'
import { JobCard } from './JobCard'
import type { JobSummaryView } from './jobTypes'

type Catalog = {
  machines: { id: number }[]
  materials: { id: number }[]
  tools: { id: number }[]
}

export function JobsPage() {
  const nav = useNavigate()
  const [jobs, setJobs] = useState<JobSummaryView[]>([])
  const [catalog, setCatalog] = useState<Catalog | null>(null)
  const [title, setTitle] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    api<JobSummaryView[]>('/jobs').then(list => {
      setJobs(list)
      console.log('[jobs] listed', list.length)
    }).catch(e => setError(e.message))
    api<Catalog>('/catalog').then(setCatalog).catch(console.error)
  }, [])

  async function createJob() {
    if (!catalog?.machines[0] || !catalog.materials[0] || !catalog.tools[0]) {
      setError('Catalog missing machine, material, or tool')
      return
    }
    setBusy(true)
    setError('')
    try {
      const job = await api<{ id: number }>('/jobs', {
        method: 'POST',
        body: JSON.stringify({
          title: title.trim() || undefined,
          machineId: catalog.machines[0].id,
          materialId: catalog.materials[0].id,
          toolId: catalog.tools[0].id,
        }),
      })
      console.log('[jobs] created', job.id, title)
      nav(`/jobs/${job.id}`)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not create job')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Shell>
      <div className="grid two action-first">
        <div className="panel grid">
          <h2>New job</h2>
          <label>Title
            <input value={title} onChange={e => setTitle(e.target.value)}
              placeholder="e.g. Kitchen cleats ×4" maxLength={120} />
          </label>
          <button type="button" onClick={createJob} disabled={busy || !catalog}>
            {busy ? 'Creating…' : 'Create job'}
          </button>
          {error && <p style={{ color: 'var(--danger)' }}>{error}</p>}
        </div>
        <div className="panel grid">
          <h2>Your jobs</h2>
          <div className="job-cards">
            {jobs.map(j => <JobCard key={j.id} job={j} />)}
          </div>
          {!jobs.length && <p className="muted">No jobs yet — create one, then add parts.</p>}
        </div>
      </div>
    </Shell>
  )
}
