import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import { Shell } from '../components/Shell'

type Job = { id: number; status: string; hasGcode: boolean }

export function JobsPage() {
  const [jobs, setJobs] = useState<Job[]>([])
  useEffect(() => {
    api<Job[]>('/jobs').then(setJobs).catch(console.error)
  }, [])
  return (
    <Shell>
      <div className="panel">
        <h2>Jobs</h2>
        {jobs.map(j => (
          <Link key={j.id} to={`/jobs/${j.id}`} className="issue">
            Job #{j.id} — {j.status}{j.hasGcode ? ' (G-code ready)' : ''}
          </Link>
        ))}
        {!jobs.length && <p className="muted">No jobs yet.</p>}
      </div>
    </Shell>
  )
}
