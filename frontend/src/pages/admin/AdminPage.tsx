import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { api } from '../../api/client'
import { Shell } from '../../components/Shell'

type Rule = { id?: number; name: string; ruleKey: string; value: number; description?: string }
type Job = { id: number; status: string }

export function AdminPage() {
  const [pricing, setPricing] = useState<Rule[]>([])
  const [jobs, setJobs] = useState<Job[]>([])
  const [machines, setMachines] = useState<{ id: number; name: string; hourlyRate: number }[]>([])
  const [msg, setMsg] = useState('')

  async function load() {
    setPricing(await api('/admin/pricing'))
    setJobs(await api('/admin/jobs'))
    setMachines(await api('/admin/machines'))
  }

  useEffect(() => { load().catch(e => setMsg(e.message)) }, [])

  async function saveRate(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const fd = new FormData(e.currentTarget)
    const rule: Rule = {
      id: Number(fd.get('id') || 0) || undefined,
      name: String(fd.get('name')),
      ruleKey: String(fd.get('ruleKey')),
      value: Number(fd.get('value')),
      description: String(fd.get('name')),
    }
    await api('/admin/pricing', { method: 'POST', body: JSON.stringify(rule) })
    setMsg('Pricing saved')
    await load()
  }

  async function setStatus(id: number, status: string) {
    await api(`/admin/jobs/${id}/status`, { method: 'PATCH', body: JSON.stringify({ status }) })
    await load()
  }

  return (
    <Shell>
      <div className="grid two">
        <div className="panel grid">
          <h2>Pricing rules</h2>
          {pricing.map(r => (
            <div key={r.id} className="issue">{r.ruleKey}: {r.value}</div>
          ))}
          <form className="grid" onSubmit={saveRate}>
            <label>Key<input name="ruleKey" defaultValue="SETUP_FEE" /></label>
            <label>Name<input name="name" defaultValue="Setup fee" /></label>
            <label>Value<input name="value" type="number" step="0.01" defaultValue={25} /></label>
            <button type="submit">Save rule</button>
          </form>
          <h3>Machines</h3>
          {machines.map(m => (
            <div key={m.id} className="issue">{m.name} — ${m.hourlyRate}/hr</div>
          ))}
          {msg && <p className="muted">{msg}</p>}
        </div>
        <div className="panel">
          <h2>Production queue</h2>
          {jobs.map(j => (
            <div key={j.id} className="issue" style={{ display: 'flex', justifyContent: 'space-between', gap: 8 }}>
              <span>Job #{j.id} — {j.status}</span>
              <button className="ghost" onClick={() => setStatus(j.id, 'IN_PRODUCTION')}>In production</button>
            </div>
          ))}
          {!jobs.length && <p className="muted">Queue empty.</p>}
        </div>
      </div>
    </Shell>
  )
}
