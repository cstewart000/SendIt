import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/client'
import { Shell } from '../components/Shell'
import { PartPicker, type DesignPart, type PartSelection } from '../components/PartPicker'
import { Viewer2D } from '../components/viewer2d/Viewer2D'
import { Viewer3D } from '../components/viewer3d/Viewer3D'

type Version = {
  id: number; analysed: boolean; partCount?: number; originalFilename?: string
  geometry?: { contours: { id?: string; closed?: boolean; points: { x: number; y: number }[] }[] }
  issues?: { id: string; category: string; severity: string; message: string; highlight?: { x: number; y: number }[] }[]
}
type Detail = { id: number; name: string; versions: Version[] }
type Catalog = { machines: { id: number }[]; materials: { id: number }[]; tools: { id: number }[] }

const FIXES = [
  ['CLOSE_OPEN_CONTOURS', 'Close open contours'],
  ['REMOVE_ZERO_LENGTH', 'Remove zero-length'],
  ['REMOVE_DUPLICATES', 'Remove duplicates'],
  ['PURGE_NON_GEOMETRY', 'Purge text/dims'],
  ['COLLAPSE_TINY', 'Collapse tiny segments'],
] as const

export function DesignWizardPage() {
  const { id } = useParams()
  const nav = useNavigate()
  const [detail, setDetail] = useState<Detail | null>(null)
  const [step, setStep] = useState(0)
  const [catalog, setCatalog] = useState<Catalog | null>(null)
  const [machIssues, setMachIssues] = useState<Version['issues']>([])
  const [parts, setParts] = useState<DesignPart[]>([])
  const [selection, setSelection] = useState<PartSelection>({})
  const [error, setError] = useState('')
  const version = detail?.versions[0]

  async function reload() {
    const d = await api<Detail>(`/designs/${id}`)
    setDetail(d)
    return d
  }

  useEffect(() => {
    Promise.all([reload(), api<Catalog>('/catalog')])
      .then(async ([d, c]) => {
        setCatalog(c)
        if (d.versions[0]) {
          const v = d.versions[0]
          const fn = (v.originalFilename || '').toLowerCase()
          const reparseKey = `dwg-linefix-v1-${v.id}`
          const needsAnalyse = !v.analysed
            || (fn.endsWith('.dwg') && !localStorage.getItem(reparseKey))
          if (needsAnalyse) {
            console.log('[wizard] analyse', v.id, fn)
            await api(`/designs/${id}/versions/${v.id}/analyse`, { method: 'POST' })
            if (fn.endsWith('.dwg')) localStorage.setItem(reparseKey, '1')
            await reload()
          }
        }
      })
      .catch(e => setError(e.message))
  }, [id])

  async function repair(action: string) {
    if (!version) return
    await api(`/designs/${id}/versions/${version.id}/repairs/${action}`, {
      method: 'POST', body: JSON.stringify({ confirm: true }),
    })
    await reload()
  }

  async function runMachinability() {
    if (!version || !catalog) return
    const issues = await api<Version['issues']>(`/designs/${id}/versions/${version.id}/machinability`, {
      method: 'POST',
      body: JSON.stringify({ toolId: catalog.tools[0].id, materialId: catalog.materials[0].id }),
    })
    setMachIssues(issues || [])
    setStep(2)
  }

  async function continueToJob() {
    const quantities = Object.entries(selection).map(([partId, quantity]) => ({
      partId: Number(partId), quantity,
    }))
    if (!version || !catalog || !quantities.length) return
    await api(`/designs/${id}/versions/${version.id}/acknowledge-warnings`, {
      method: 'POST', body: JSON.stringify({ acknowledge: true }),
    })
    const job = await api<{ id: number }>('/jobs', {
      method: 'POST',
      body: JSON.stringify({
        title: detail?.name || undefined,
        machineId: catalog.machines[0].id,
        materialId: catalog.materials[0].id,
        toolId: catalog.tools[0].id,
      }),
    })
    await api(`/jobs/${job.id}/parts`, {
      method: 'POST',
      body: JSON.stringify({ designVersionId: version.id, quantities }),
    })
    nav(`/jobs/${job.id}`)
  }

  const selectedIds = Object.keys(selection).map(Number)
  const totalPieces = Object.values(selection).reduce((a, b) => a + b, 0)
  const highlight = useMemo(
    () => parts.filter(p => selectedIds.includes(p.id)).flatMap(p => p.geometry?.contours || []),
    [parts, selection],
  )
  const contours = version?.geometry?.contours || []
  const issues = step >= 2 ? machIssues : version?.issues || []

  return (
    <Shell>
      <div className="steps">
        {['Upload', 'Repair', 'Parts & job'].map((s, i) => (
          <span key={s} className={i === step ? 'on' : ''}>{s}</span>
        ))}
      </div>
      {error && <p style={{ color: 'var(--danger)' }}>{error}</p>}
      <div className="grid two">
        <div className="panel">
          <h2>{detail?.name || 'Design'}</h2>
          <Viewer2D contours={highlight.length ? highlight : contours} issues={issues} />
          <div style={{ marginTop: '1rem' }}>
            <Viewer3D contours={highlight.length ? highlight : contours} />
          </div>
        </div>
        <div className="panel grid">
          {step <= 1 && (
            <>
              <h3>Guided repair</h3>
              {(version?.issues || []).map(i => (
                <div key={i.id} className={`issue ${i.severity}`}>{i.category}: {i.message}</div>
              ))}
              {FIXES.map(([code, label]) => (
                <button key={code} className="ghost" onClick={() => repair(code)}>{label}</button>
              ))}
              <button onClick={() => { setStep(2); runMachinability() }}>Continue to parts</button>
            </>
          )}
          {step === 2 && version && (
            <>
              <h3>Select parts & quantities</h3>
              <p className="muted">Set qty per part, or use “Set all” for multiples of every part.</p>
              <PartPicker designId={id!} versionId={version.id} selection={selection}
                onChange={setSelection} onPartsLoaded={setParts} />
              <button onClick={continueToJob} disabled={!selectedIds.length}>
                Create job ({selectedIds.length} types, {totalPieces} pieces)
              </button>
            </>
          )}
        </div>
      </div>
    </Shell>
  )
}
