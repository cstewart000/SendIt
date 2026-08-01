import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/client'
import { Shell } from '../components/Shell'
import { PartPicker, type DesignPart, type PartSelection } from '../components/PartPicker'
import { Viewer2D } from '../components/viewer2d/Viewer2D'
import { Viewer3D } from '../components/viewer3d/Viewer3D'

type Version = {
  id: number; analysed: boolean; repaired?: boolean; partCount?: number; originalFilename?: string
  geometry?: { contours: { id?: string; closed?: boolean; points: { x: number; y: number }[] }[] }
  issues?: { id: string; category: string; severity: string; message: string; highlight?: { x: number; y: number }[] }[]
}
type Detail = { id: number; name: string; versions: Version[] }
type Catalog = {
  machines: { id: number; name?: string }[]
  materials: { id: number; name?: string }[]
  tools: { id: number; name: string; diameterMm: number; type?: string }[]
}

const FIXES = [
  ['CLOSE_OPEN_CONTOURS', 'Close open contours'],
  ['REMOVE_ZERO_LENGTH', 'Remove zero-length'],
  ['REMOVE_DUPLICATES', 'Remove duplicates'],
  ['PURGE_NON_GEOMETRY', 'Purge text/dims'],
  ['COLLAPSE_TINY', 'Collapse tiny segments'],
] as const

const DOG_SCALES = [
  { value: 0.6, label: 'Light (0.6× tool radius)' },
  { value: 1.0, label: 'Standard (1× tool radius)' },
  { value: 1.2, label: 'Heavy (1.2× tool radius)' },
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
  const [busy, setBusy] = useState(false)
  const [dogToolId, setDogToolId] = useState<number | ''>('')
  const [dogScale, setDogScale] = useState(1.0)
  const [dogPreview, setDogPreview] = useState<{ candidates: number; message: string; canUndo?: boolean } | null>(null)
  const [dogMsg, setDogMsg] = useState('')
  const [canUndoDogbones, setCanUndoDogbones] = useState(false)
  const version = detail?.versions[0]

  async function reload() {
    const d = await api<Detail>(`/designs/${id}`)
    setDetail(d)
    return d
  }

  async function loadParts(versionId: number) {
    try {
      const list = await api<DesignPart[]>(`/designs/${id}/versions/${versionId}/parts`)
      setParts(list)
      if (!Object.keys(selection).length && list.length) {
        const next: PartSelection = {}
        list.forEach(p => { next[p.id] = 1 })
        setSelection(next)
      }
    } catch {
      /* parts may not exist until analyse */
    }
  }

  async function refreshDogPreview(versionId: number, toolId?: number) {
    try {
      const q = toolId ? `?toolId=${toolId}` : ''
      const p = await api<{ candidates: number; message: string; canUndo?: boolean }>(
        `/designs/${id}/versions/${versionId}/dogbones/preview${q}`,
      )
      setDogPreview(p)
      setCanUndoDogbones(!!p.canUndo)
    } catch {
      setDogPreview(null)
    }
  }

  useEffect(() => {
    Promise.all([reload(), api<Catalog>('/catalog')])
      .then(async ([d, c]) => {
        setCatalog(c)
        if (c.tools?.length) {
          const preferred = c.tools.find(t => (t.type || '').toUpperCase() === 'ENDMILL') || c.tools[0]
          setDogToolId(preferred.id)
        }
        if (d.versions[0]) {
          const v = d.versions[0]
          const fn = (v.originalFilename || '').toLowerCase()
          const reparseKey = `dwg-linefix-v1-${v.id}`
          const joinFixKey = `contour-join-v2-${v.id}`
          const needsAnalyse = !v.analysed
            || (fn.endsWith('.dwg') && !localStorage.getItem(reparseKey))
            || !localStorage.getItem(joinFixKey)
          if (needsAnalyse) {
            console.log('[wizard] analyse', v.id, fn)
            await api(`/designs/${id}/versions/${v.id}/analyse`, { method: 'POST' })
            if (fn.endsWith('.dwg')) localStorage.setItem(reparseKey, '1')
            localStorage.setItem(joinFixKey, '1')
            await reload()
          }
          await loadParts(v.id)
          await refreshDogPreview(v.id, c.tools?.[0]?.id)
        }
      })
      .catch(e => setError(e.message))
  }, [id])

  useEffect(() => {
    if (version && dogToolId !== '') {
      void refreshDogPreview(version.id, Number(dogToolId))
    }
  }, [dogToolId, version?.id])

  async function repair(action: string) {
    if (!version) return
    setBusy(true)
    setError('')
    try {
      await api(`/designs/${id}/versions/${version.id}/repairs/${action}`, {
        method: 'POST', body: JSON.stringify({ confirm: true }),
      })
      const d = await reload()
      if (d.versions[0]) {
        await loadParts(d.versions[0].id)
        await refreshDogPreview(d.versions[0].id, dogToolId === '' ? undefined : Number(dogToolId))
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Repair failed')
    } finally {
      setBusy(false)
    }
  }

  async function applyDogbones() {
    if (!version || dogToolId === '') return
    setBusy(true)
    setError('')
    setDogMsg('')
    try {
      const res = await api<{
        dogBonesAdded: number
        radiusMm: number
        message: string
        pointsBefore?: number
        pointsAfter?: number
        pointsStored?: number
        canUndo?: boolean
        version?: Version
        geometry?: Version['geometry']
      }>(`/designs/${id}/versions/${version.id}/dogbones`, {
        method: 'POST',
        body: JSON.stringify({
          toolId: Number(dogToolId),
          scale: dogScale,
          confirm: true,
        }),
      })
      // Immediately patch local model so viewers update without waiting for reload
      if (res.version || res.geometry) {
        setDetail(prev => {
          if (!prev) return prev
          const vs = prev.versions.map(v => {
            if (v.id !== version.id) return v
            const merged = { ...v, ...(res.version || {}) }
            if (res.geometry) merged.geometry = res.geometry
            if (res.version?.geometry) merged.geometry = res.version.geometry
            return merged
          })
          return { ...prev, versions: vs }
        })
      }
      const ptsNote = res.pointsStored != null
        ? ` · model points ${res.pointsBefore ?? '?'}→${res.pointsStored}`
        : ''
      setDogMsg((res.message || `Added ${res.dogBonesAdded} dog-bone(s)`) + ptsNote)
      setCanUndoDogbones(res.canUndo !== false)
      // Full reload from API to confirm geometryJson round-trip
      const d = await reload()
      if (d.versions[0]) {
        await loadParts(d.versions[0].id)
        await refreshDogPreview(d.versions[0].id, Number(dogToolId))
      }
      console.log('[wizard] dogbones applied', res.dogBonesAdded, 'points', res.pointsBefore, '→', res.pointsStored)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Dog-bone apply failed')
    } finally {
      setBusy(false)
    }
  }

  async function undoDogbones() {
    if (!version) return
    setBusy(true)
    setError('')
    setDogMsg('')
    try {
      const res = await api<{
        message: string
        canUndo?: boolean
        pointsStored?: number
        version?: Version
        geometry?: Version['geometry']
      }>(`/designs/${id}/versions/${version.id}/dogbones/undo`, { method: 'POST' })
      if (res.version || res.geometry) {
        setDetail(prev => {
          if (!prev) return prev
          const vs = prev.versions.map(v => {
            if (v.id !== version.id) return v
            const merged = { ...v, ...(res.version || {}) }
            if (res.geometry) merged.geometry = res.geometry
            if (res.version?.geometry) merged.geometry = res.version.geometry
            return merged
          })
          return { ...prev, versions: vs }
        })
      }
      setDogMsg(res.message || 'Dog-bones undone')
      setCanUndoDogbones(false)
      const d = await reload()
      if (d.versions[0]) {
        await loadParts(d.versions[0].id)
        await refreshDogPreview(d.versions[0].id, dogToolId === '' ? undefined : Number(dogToolId))
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Undo dog-bones failed')
    } finally {
      setBusy(false)
    }
  }

  async function runMachinability() {
    if (!version || !catalog) return
    setBusy(true)
    try {
      const toolId = dogToolId !== '' ? Number(dogToolId) : catalog.tools[0].id
      const issues = await api<Version['issues']>(`/designs/${id}/versions/${version.id}/machinability`, {
        method: 'POST',
        body: JSON.stringify({ toolId, materialId: catalog.materials[0].id }),
      })
      setMachIssues(issues || [])
      setStep(2)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Machinability check failed')
    } finally {
      setBusy(false)
    }
  }

  async function continueToJob() {
    const quantities = Object.entries(selection).map(([partId, quantity]) => ({
      partId: Number(partId), quantity,
    }))
    if (!version || !catalog || !quantities.length) return
    const toolId = dogToolId !== '' ? Number(dogToolId) : catalog.tools[0].id
    await api(`/designs/${id}/versions/${version.id}/acknowledge-warnings`, {
      method: 'POST', body: JSON.stringify({ acknowledge: true }),
    })
    const job = await api<{ id: number }>('/jobs', {
      method: 'POST',
      body: JSON.stringify({
        title: detail?.name || undefined,
        machineId: catalog.machines[0].id,
        materialId: catalog.materials[0].id,
        toolId,
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
  const partsFor3d = useMemo(() => (parts.length ? parts : []), [parts])
  const issues = step >= 2 ? machIssues : version?.issues || []
  const selectedTool = catalog?.tools.find(t => t.id === dogToolId)
  const dogRadius = selectedTool ? (selectedTool.diameterMm / 2) * dogScale : 0
  // Force viewers to remount when geometry topology changes (e.g. dog-bones)
  const geoKey = useMemo(() => {
    const cs = highlight.length ? highlight : contours
    const n = cs.reduce((a, c) => a + (c.points?.length || 0), 0)
    return `${version?.id || 0}-${cs.length}-${n}-${version?.repaired ? 1 : 0}`
  }, [version?.id, version?.repaired, contours, highlight])

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
          <Viewer2D key={`2d-${geoKey}`} contours={highlight.length ? highlight : contours} issues={issues} />
          <div style={{ marginTop: '1rem' }}>
            <p className="muted" style={{ margin: '0 0 0.4rem', fontSize: '0.85rem' }}>
              3D: drag rotate · right-drag pan · scroll zoom
              {partsFor3d.length > 0 ? ` · ${partsFor3d.length} part(s)` : ''}
            </p>
            <Viewer3D
              key={`3d-${geoKey}`}
              parts={partsFor3d.length ? partsFor3d : undefined}
              contours={partsFor3d.length ? undefined : contours}
            />
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
                <button key={code} className="ghost" disabled={busy} onClick={() => repair(code)}>{label}</button>
              ))}

              <div className="panel" style={{ padding: '0.85rem', border: '1px solid var(--line)', marginTop: 4 }}>
                <h3 style={{ marginTop: 0 }}>Dog-bones</h3>
                <p className="muted" style={{ margin: '0 0 0.6rem', fontSize: '0.88rem' }}>
                  Circular clearance at sharp internal corners. The circle centre is offset into the material
                  so the original corner sits on the rim (Fusion-style); straight edges stay unchanged.
                </p>
                {dogPreview && (
                  <p className="muted" style={{ margin: '0 0 0.6rem' }}>
                    {dogPreview.message}
                  </p>
                )}
                <label>Tool (sets dog-bone radius)
                  <select
                    value={dogToolId === '' ? '' : String(dogToolId)}
                    onChange={e => setDogToolId(e.target.value ? Number(e.target.value) : '')}
                    disabled={busy}
                  >
                    <option value="">Select tool…</option>
                    {(catalog?.tools || []).map(t => (
                      <option key={t.id} value={t.id}>
                        {t.name} — Ø{t.diameterMm} mm
                      </option>
                    ))}
                  </select>
                </label>
                <label>Size
                  <select
                    value={String(dogScale)}
                    onChange={e => setDogScale(Number(e.target.value))}
                    disabled={busy}
                  >
                    {DOG_SCALES.map(s => (
                      <option key={s.value} value={s.value}>{s.label}</option>
                    ))}
                  </select>
                </label>
                {selectedTool && (
                  <p className="muted" style={{ margin: 0, fontSize: '0.85rem' }}>
                    Circle radius ≈ <strong>{dogRadius.toFixed(2)} mm</strong>
                    {' '}(½ × Ø{selectedTool.diameterMm} × {dogScale})
                  </p>
                )}
                <div className="row" style={{ marginTop: 8, flexWrap: 'wrap', gap: 8 }}>
                  <button
                    type="button"
                    disabled={busy || dogToolId === '' || (dogPreview?.candidates ?? 0) === 0}
                    onClick={applyDogbones}
                  >
                    Apply dog-bones
                    {dogPreview && dogPreview.candidates > 0 ? ` (${dogPreview.candidates})` : ''}
                  </button>
                  <button
                    type="button"
                    className="ghost"
                    disabled={busy || !canUndoDogbones}
                    onClick={undoDogbones}
                    title={canUndoDogbones ? 'Restore geometry from before dog-bones' : 'Nothing to undo'}
                  >
                    Undo dog-bones
                  </button>
                  <button
                    type="button"
                    className="ghost"
                    disabled={busy || !version}
                    onClick={() => version && refreshDogPreview(version.id, dogToolId === '' ? undefined : Number(dogToolId))}
                  >
                    Refresh count
                  </button>
                </div>
                {dogMsg && <p style={{ color: 'var(--ok)', margin: '0.5rem 0 0' }}>{dogMsg}</p>}
              </div>

              <button disabled={busy} onClick={() => { void runMachinability() }}>
                Continue to parts
              </button>
            </>
          )}
          {step === 2 && version && (
            <>
              <h3>Select parts & quantities</h3>
              <p className="muted">Set qty per part, or use “Set all” for multiples of every part.</p>
              {(machIssues || []).length > 0 && (
                <div>
                  <h4 style={{ marginBottom: 4 }}>Machinability</h4>
                  {machIssues!.map(i => (
                    <div key={i.id} className={`issue ${i.severity}`}>{i.category}: {i.message}</div>
                  ))}
                  <button type="button" className="ghost" disabled={busy}
                    onClick={() => { setStep(1) }}>
                    Back to repair / dog-bones
                  </button>
                </div>
              )}
              <PartPicker designId={id!} versionId={version.id} selection={selection}
                onChange={setSelection} onPartsLoaded={setParts} />
              <button onClick={continueToJob} disabled={!selectedIds.length || busy}>
                Create job ({selectedIds.length} types, {totalPieces} pieces)
              </button>
            </>
          )}
        </div>
      </div>
    </Shell>
  )
}
