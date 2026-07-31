import { useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import { api, apiBlob } from '../api/client'
import { Shell } from '../components/Shell'
import { PartPicker, type PartSelection } from '../components/PartPicker'
import { NestEditor } from '../components/viewer2d/NestEditor'
import { GCodeViewer, type ToolpathData } from '../components/viewer2d/GCodeViewer'
import type { NestShape } from '../components/viewer2d/nestDraw'
import type { NestPlacement } from '../components/viewer2d/nestMath'

type JobPart = {
  id: number; label: string; quantity: number; widthMm: number; heightMm: number
  designVersionId: number; designPartId?: number
}
type Job = {
  id: number; title?: string; status: string; nestingLocked: boolean; hasGcode: boolean
  marginMm?: number; partGapMm?: number
  parts?: JobPart[]
  nesting?: {
    sheetWidth: number; sheetHeight: number; sheetCount: number
    placements: NestPlacement[]
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
  const [titleDraft, setTitleDraft] = useState('')
  const [shapes, setShapes] = useState<Record<number, NestShape>>({})
  const [toolpath, setToolpath] = useState<ToolpathData | null>(null)
  const [toolpathErr, setToolpathErr] = useState('')
  const [camBusy, setCamBusy] = useState(false)
  const nestSave = useRef<ReturnType<typeof setTimeout> | null>(null)

  async function loadShapes() {
    const list = await api<NestShape[]>(`/jobs/${id}/nest-shapes`)
    const map: Record<number, NestShape> = {}
    list.forEach(s => { map[s.jobPartId] = s })
    setShapes(map)
    console.log('[job] nest shapes', list.length)
  }

  async function reload() {
    const j = await api<Job>(`/jobs/${id}`)
    setJob(j)
    setTitleDraft(j.title || `Job #${j.id}`)
    if (j.parts?.length) await loadShapes().catch(console.error)
  }

  async function saveTitle() {
    const title = titleDraft.trim() || `Job #${id}`
    setJob(await api<Job>(`/jobs/${id}`, {
      method: 'PATCH', body: JSON.stringify({ title }),
    }))
    setTitleDraft(title)
    console.log('[job] title', title)
  }

  useEffect(() => {
    reload()
      .then(async () => {
        // Load toolpath if already nested
        try {
          const j = await api<Job>(`/jobs/${id}`)
          if (j.nesting?.placements?.length) await loadToolpath()
        } catch { /* ignore */ }
      })
      .catch(e => setError(e.message))
    api<DesignSum[]>('/designs').then(setDesigns).catch(console.error)
  }, [id])

  async function pickDesign(raw: string) {
    if (!raw) {
      setDesignId('')
      setVersionId('')
      setSelection({})
      return
    }
    const did = Number(raw)
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
      method: 'POST', body: JSON.stringify({ designVersionId: versionId, quantities }),
    }))
    setSelection({})
    console.log('[job] added parts', quantities.length)
    await loadShapes().catch(console.error)
  }

  async function setJobPartQty(jobPartId: number, quantity: number) {
    setJob(await api<Job>(`/jobs/${id}/parts`, {
      method: 'PATCH',
      body: JSON.stringify({ updates: [{ jobPartId, quantity: Math.max(1, quantity) }] }),
    }))
  }

  async function setAllJobQty() {
    setJob(await api<Job>(`/jobs/${id}/parts`, {
      method: 'PATCH', body: JSON.stringify({ allQuantity: Math.max(1, allQty) }),
    }))
  }

  async function loadToolpath() {
    setToolpathErr('')
    try {
      const tp = await api<ToolpathData>(`/jobs/${id}/toolpath`)
      setToolpath(tp)
      console.log('[job] toolpath paths', tp.paths?.length, 'fixings', tp.fixings?.length)
    } catch (e) {
      setToolpath(null)
      setToolpathErr(e instanceof Error ? e.message : 'Toolpath failed')
    }
  }

  async function patchCamOptions(body: {
    disabledFixingIds?: string[]
    tabsEnabled?: boolean
    fixingsEnabled?: boolean
  }) {
    setCamBusy(true)
    setToolpathErr('')
    try {
      const tp = await api<ToolpathData>(`/jobs/${id}/cam-options`, {
        method: 'PATCH', body: JSON.stringify(body),
      })
      setToolpath(tp)
    } catch (e) {
      setToolpathErr(e instanceof Error ? e.message : 'CAM options failed')
    } finally {
      setCamBusy(false)
    }
  }

  function toggleFixing(fid: string, enabled: boolean) {
    const all = toolpath?.fixings || []
    const disabled = all
      .filter(f => (f.id === fid ? !enabled : !f.enabled))
      .map(f => f.id)
    void patchCamOptions({ disabledFixingIds: disabled })
  }

  async function nest() {
    const j = await api<Job>(`/jobs/${id}/nest`, { method: 'POST' })
    setJob(j)
    await loadToolpath().catch(console.error)
  }
  async function lock() { setJob(await api<Job>(`/jobs/${id}/lock-nesting`, { method: 'POST' })) }
  async function quote() { setJob(await api<Job>(`/jobs/${id}/quote`, { method: 'POST' })) }
  async function approve() {
    const j = await api<Job>(`/jobs/${id}/approve`, { method: 'POST' })
    setJob(j)
    await loadToolpath().catch(console.error)
  }

  function onNestChange(placements: NestPlacement[]) {
    setJob(j => (j?.nesting ? { ...j, nesting: { ...j.nesting, placements } } : j))
    if (nestSave.current) clearTimeout(nestSave.current)
    nestSave.current = setTimeout(async () => {
      try {
        setJob(await api<Job>(`/jobs/${id}/nesting`, {
          method: 'PATCH', body: JSON.stringify({ placements }),
        }))
        console.log('[job] nest adjusted', placements.length)
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Nest adjust failed')
      }
    }, 350)
  }

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
          <label>Job title
            <div className="row">
              <input value={titleDraft} onChange={e => setTitleDraft(e.target.value)}
                onBlur={saveTitle} maxLength={120}
                onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); saveTitle() } }} />
              <button type="button" className="ghost" onClick={saveTitle}>Save</button>
            </div>
          </label>
          <p className="muted">#{job?.id} · {job?.status}</p>
          {job?.nesting ? (
            <NestEditor
              sheet={{ width: job.nesting.sheetWidth, height: job.nesting.sheetHeight }}
              placements={job.nesting.placements || []}
              shapes={shapes}
              locked={job.nestingLocked}
              onChange={onNestChange}
            />
          ) : (
            <p className="muted">Run auto-nest to place parts on the sheet.</p>
          )}
          <p className="muted">
            Sheets: {job?.nesting?.sheetCount ?? 0}
            {job?.marginMm != null && ` · edge margin ${job.marginMm} mm`}
            {job?.partGapMm != null && ` · part gap ${job.partGapMm} mm`}
          </p>
          {job?.nesting?.placements?.length ? (
            <div style={{ marginTop: '1rem' }}>
              <div className="row" style={{ marginBottom: 8 }}>
                <h3 style={{ margin: 0, flex: 1 }}>G-code / toolpath</h3>
                <button type="button" className="ghost" onClick={loadToolpath}>Refresh preview</button>
              </div>
              {toolpathErr && <p style={{ color: 'var(--danger)' }}>{toolpathErr}</p>}
              <GCodeViewer
                data={toolpath}
                busy={camBusy}
                onToggleFixing={toggleFixing}
                onCamOptions={opts => patchCamOptions(opts)}
              />
            </div>
          ) : null}
          <h3>Parts on job</h3>
          {!job?.nestingLocked && (job?.parts?.length ?? 0) > 0 && (
            <div className="row" style={{ marginBottom: 8 }}>
              <input className="qty" type="number" min={1} value={allQty}
                onChange={e => setAllQty(Number(e.target.value) || 1)} />
              <button type="button" className="ghost" onClick={setAllJobQty}>Set all parts to ×{allQty}</button>
            </div>
          )}
          {(job?.parts || []).map(p => (
            <div key={p.id} className="issue" style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
              <span style={{ flex: 1 }}>{p.label} — {p.widthMm.toFixed(0)}×{p.heightMm.toFixed(0)} mm</span>
              {job?.nestingLocked ? <span>×{p.quantity}</span> : (
                <input className="qty" type="number" min={1} defaultValue={p.quantity}
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
                <select value={designId === '' ? '' : String(designId)}
                  onChange={e => pickDesign(e.target.value)}>
                  <option value="">Select…</option>
                  {designs.map(d => (
                    <option key={d.id} value={d.id}>
                      {d.name}{d.partCount != null ? ` (${d.partCount} parts)` : ''}
                    </option>
                  ))}
                </select>
              </label>
              {designId !== '' && versionId !== '' ? (
                <PartPicker designId={designId} versionId={Number(versionId)}
                  selection={selection} onChange={setSelection} />
              ) : null}
              <button className="ghost" onClick={addParts} disabled={!addTotal}>
                Add to job{addTotal ? ` · ${addTotal} pcs` : ''}
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
