import { useCallback, useEffect, useRef, useState, type PointerEvent as REPointerEvent, type WheelEvent as REWheelEvent } from 'react'

export type ToolpathPt = { x: number; y: number }
export type ToolpathPath = {
  kind: string
  hole: boolean
  sheetIndex: number
  label?: string
  points: ToolpathPt[]
}
export type ToolpathFixing = {
  id: string
  sheetIndex: number
  x: number
  y: number
  diameterMm: number
  enabled: boolean
  label?: string
}
export type ToolpathTab = {
  id: string
  sheetIndex: number
  x: number
  y: number
  widthMm: number
  heightMm: number
  segment?: ToolpathPt[]
}
export type ToolpathData = {
  sheetWidth: number
  sheetHeight: number
  sheetCount: number
  toolDiameterMm: number
  toolName: string
  fixingHoleDiameterMm?: number
  fixingMinToolDistanceMm?: number
  tabWidthMm?: number
  tabHeightMm?: number
  tabsEnabled?: boolean
  fixingsEnabled?: boolean
  paths: ToolpathPath[]
  fixings?: ToolpathFixing[]
  tabs?: ToolpathTab[]
  gcode?: string
}

type View = { scale: number; ox: number; oy: number }

const W = 720
const H = 420
const PAD = 24

function fit(sheetW: number, sheetH: number): View {
  const scale = Math.min((W - PAD * 2) / Math.max(1, sheetW), (H - PAD * 2) / Math.max(1, sheetH))
  return { scale, ox: PAD, oy: H - PAD }
}

type Props = {
  data: ToolpathData | null
  sheetIndex?: number
  showGcode?: boolean
  onToggleFixing?: (id: string, enabled: boolean) => void
  onCamOptions?: (opts: { tabsEnabled?: boolean; fixingsEnabled?: boolean }) => void
  busy?: boolean
}

export function GCodeViewer({
  data, sheetIndex = 0, showGcode = true, onToggleFixing, onCamOptions, busy,
}: Props) {
  const ref = useRef<HTMLCanvasElement>(null)
  const [view, setView] = useState<View>(() => fit(2440, 1220))
  const pan = useRef<{ x0: number; y0: number; ox: number; oy: number } | null>(null)
  const [sheet, setSheet] = useState(sheetIndex)

  useEffect(() => {
    if (data) setView(fit(data.sheetWidth, data.sheetHeight))
  }, [data?.sheetWidth, data?.sheetHeight, data?.paths?.length, data?.fixings?.length])

  useEffect(() => { setSheet(sheetIndex) }, [sheetIndex])

  const draw = useCallback(() => {
    const canvas = ref.current
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    if (!ctx) return
    const { scale, ox, oy } = view
    const tx = (x: number) => ox + x * scale
    const ty = (y: number) => oy - y * scale

    ctx.clearRect(0, 0, W, H)
    ctx.fillStyle = '#0f1720'
    ctx.fillRect(0, 0, W, H)

    if (!data) {
      ctx.fillStyle = '#94a3b8'
      ctx.font = '14px IBM Plex Sans, sans-serif'
      ctx.fillText('Nest the job to preview toolpaths', 24, 40)
      return
    }

    const sw = data.sheetWidth
    const sh = data.sheetHeight
    const d = data.toolDiameterMm || 6

    ctx.fillStyle = '#1e293b'
    ctx.fillRect(tx(0), ty(sh), sw * scale, sh * scale)
    ctx.strokeStyle = '#64748b'
    ctx.lineWidth = 1.5
    ctx.strokeRect(tx(0), ty(sh), sw * scale, sh * scale)

    // Tabs (bridges)
    for (const tab of (data.tabs || []).filter(t => (t.sheetIndex || 0) === sheet)) {
      if (tab.segment && tab.segment.length >= 2) {
        ctx.strokeStyle = '#a855f7'
        ctx.lineWidth = Math.max(3, (data.tabWidthMm || 5) * scale * 0.15)
        ctx.lineCap = 'round'
        ctx.beginPath()
        tab.segment.forEach((p, i) => {
          if (i === 0) ctx.moveTo(tx(p.x), ty(p.y))
          else ctx.lineTo(tx(p.x), ty(p.y))
        })
        ctx.stroke()
      } else {
        ctx.fillStyle = '#a855f7'
        ctx.beginPath()
        ctx.arc(tx(tab.x), ty(tab.y), 4, 0, Math.PI * 2)
        ctx.fill()
      }
    }

    // Cuts / drills / rapids
    const paths = data.paths.filter(p => (p.sheetIndex || 0) === sheet)
    for (const path of paths) {
      if (!path.points?.length) continue
      if (path.kind === 'rapid') {
        const p = path.points[0]
        ctx.fillStyle = path.hole ? '#fbbf24' : '#38bdf8'
        ctx.beginPath()
        ctx.arc(tx(p.x), ty(p.y), 2.5, 0, Math.PI * 2)
        ctx.fill()
        continue
      }
      if (path.kind === 'drill') {
        ctx.strokeStyle = '#f472b6'
        ctx.lineWidth = 1.5
        ctx.beginPath()
        path.points.forEach((p, i) => {
          if (i === 0) ctx.moveTo(tx(p.x), ty(p.y))
          else ctx.lineTo(tx(p.x), ty(p.y))
        })
        ctx.stroke()
        continue
      }
      ctx.beginPath()
      path.points.forEach((p, i) => {
        const x = tx(p.x), y = ty(p.y)
        if (i === 0) ctx.moveTo(x, y)
        else ctx.lineTo(x, y)
      })
      ctx.strokeStyle = path.hole ? '#f59e0b' : '#22c55e'
      ctx.lineWidth = Math.max(1.2, d * scale * 0.35)
      ctx.globalAlpha = 0.85
      ctx.stroke()
      ctx.globalAlpha = 1
      ctx.strokeStyle = path.hole ? '#fde68a' : '#bbf7d0'
      ctx.lineWidth = 1
      ctx.stroke()
    }

    // Screw fixings (enabled + disabled)
    for (const f of (data.fixings || []).filter(f => (f.sheetIndex || 0) === sheet)) {
      const r = Math.max(2, (f.diameterMm / 2) * scale)
      ctx.beginPath()
      ctx.arc(tx(f.x), ty(f.y), r, 0, Math.PI * 2)
      if (f.enabled) {
        ctx.fillStyle = 'rgba(244, 114, 182, 0.35)'
        ctx.strokeStyle = '#f472b6'
        ctx.fill()
      } else {
        ctx.strokeStyle = '#64748b'
        ctx.setLineDash([3, 3])
      }
      ctx.lineWidth = 1.5
      ctx.stroke()
      ctx.setLineDash([])
      // crosshair
      ctx.beginPath()
      ctx.moveTo(tx(f.x) - r, ty(f.y))
      ctx.lineTo(tx(f.x) + r, ty(f.y))
      ctx.moveTo(tx(f.x), ty(f.y) - r)
      ctx.lineTo(tx(f.x), ty(f.y) + r)
      ctx.stroke()
    }

    ctx.fillStyle = '#e2e8f0'
    ctx.font = '12px IBM Plex Sans, sans-serif'
    ctx.fillText(`${data.toolName || 'Tool'} · sheet ${sheet + 1}/${Math.max(1, data.sheetCount)}`, 12, 18)
    ctx.fillStyle = '#22c55e'
    ctx.fillText('outer', 12, H - 12)
    ctx.fillStyle = '#f59e0b'
    ctx.fillText('hole', 60, H - 12)
    ctx.fillStyle = '#f472b6'
    ctx.fillText('screw', 105, H - 12)
    ctx.fillStyle = '#a855f7'
    ctx.fillText('tab', 160, H - 12)
  }, [view, data, sheet])

  useEffect(() => { draw() }, [draw])

  function canvasXY(e: { clientX: number; clientY: number }) {
    const canvas = ref.current!
    const r = canvas.getBoundingClientRect()
    return {
      x: (e.clientX - r.left) * (canvas.width / r.width),
      y: (e.clientY - r.top) * (canvas.height / r.height),
    }
  }

  function onWheel(e: REWheelEvent) {
    e.preventDefault()
    const { x, y } = canvasXY(e)
    const factor = e.deltaY < 0 ? 1.12 : 1 / 1.12
    setView(v => {
      const next = Math.min(50, Math.max(0.02, v.scale * factor))
      const k = next / v.scale
      return { scale: next, ox: x - (x - v.ox) * k, oy: y - (y - v.oy) * k }
    })
  }

  function onDown(e: REPointerEvent) {
    // Click near a fixing toggles it
    if (data && onToggleFixing && e.button === 0) {
      const { x, y } = canvasXY(e)
      const { scale, ox, oy } = view
      const wx = (x - ox) / scale
      const wy = (oy - y) / scale
      const hit = (data.fixings || [])
        .filter(f => (f.sheetIndex || 0) === sheet)
        .find(f => Math.hypot(f.x - wx, f.y - wy) < Math.max(f.diameterMm, 8))
      if (hit) {
        onToggleFixing(hit.id, !hit.enabled)
        return
      }
    }
    const { x, y } = canvasXY(e)
    pan.current = { x0: x, y0: y, ox: view.ox, oy: view.oy }
    ;(e.target as HTMLElement).setPointerCapture(e.pointerId)
  }

  function onMove(e: REPointerEvent) {
    if (!pan.current) return
    const { x, y } = canvasXY(e)
    const d = pan.current
    setView(v => ({ ...v, ox: d.ox + (x - d.x0), oy: d.oy + (y - d.y0) }))
  }

  if (!data) {
    return <p className="muted">Run auto-nest, then open toolpath preview.</p>
  }

  const maxSheet = Math.max(0, (data.sheetCount || 1) - 1)
  const fixings = data.fixings || []
  const sheetFixings = fixings.filter(f => (f.sheetIndex || 0) === sheet)
  const enabledCount = fixings.filter(f => f.enabled).length

  return (
    <div>
      <div className="row" style={{ marginBottom: 8, flexWrap: 'wrap' }}>
        <strong style={{ flex: 1 }}>
          Toolpath · {data.toolName} Ø{data.toolDiameterMm} mm
        </strong>
        {maxSheet > 0 && (
          <label style={{ display: 'flex', gap: 6, alignItems: 'center', margin: 0 }}>
            Sheet
            <select value={sheet} onChange={e => setSheet(Number(e.target.value))}>
              {Array.from({ length: maxSheet + 1 }, (_, i) => (
                <option key={i} value={i}>{i + 1}</option>
              ))}
            </select>
          </label>
        )}
        <button type="button" className="ghost" onClick={() => setView(fit(data.sheetWidth, data.sheetHeight))}>
          Fit
        </button>
      </div>
      <canvas
        ref={ref}
        width={W}
        height={H}
        onWheel={onWheel}
        onPointerDown={onDown}
        onPointerMove={onMove}
        onPointerUp={() => { pan.current = null }}
        onPointerCancel={() => { pan.current = null }}
        onContextMenu={e => e.preventDefault()}
        style={{
          width: '100%',
          borderRadius: 8,
          border: '1px solid var(--line)',
          touchAction: 'none',
          cursor: 'grab',
          background: '#0f1720',
        }}
      />
      <p className="muted" style={{ margin: '0.35rem 0', fontSize: '0.82rem' }}>
        Click a pink cross to enable/disable a screw hole · drag pan · scroll zoom
      </p>

      {/* Feature toggles */}
      <div className="row" style={{ flexWrap: 'wrap', marginBottom: 8 }}>
        <label className="row" style={{ alignItems: 'center', gap: 6, margin: 0 }}>
          <input type="checkbox" disabled={busy || !onCamOptions}
            checked={data.fixingsEnabled !== false}
            onChange={e => onCamOptions?.({ fixingsEnabled: e.target.checked })} />
          Screw fixings
        </label>
        <label className="row" style={{ alignItems: 'center', gap: 6, margin: 0 }}>
          <input type="checkbox" disabled={busy || !onCamOptions}
            checked={data.tabsEnabled !== false}
            onChange={e => onCamOptions?.({ tabsEnabled: e.target.checked })} />
          Hold-down tabs
        </label>
        <span className="muted" style={{ fontSize: '0.85rem' }}>
          Min fixing→toolpath {data.fixingMinToolDistanceMm ?? 10} mm · hole Ø{data.fixingHoleDiameterMm ?? 4} mm
          {data.tabsEnabled !== false && ` · tabs ${data.tabWidthMm ?? 5}×${data.tabHeightMm ?? 1.5} mm`}
        </span>
      </div>

      {/* Screw checklist */}
      {sheetFixings.length > 0 && (
        <div className="panel" style={{ padding: '0.75rem', marginBottom: 8 }}>
          <strong>Screw locations</strong>
          <span className="muted"> · {enabledCount}/{fixings.length} enabled</span>
          <div style={{ display: 'grid', gap: 4, marginTop: 8, maxHeight: 160, overflow: 'auto' }}>
            {sheetFixings.map(f => (
              <label key={f.id} className="row" style={{ alignItems: 'center', gap: 8, margin: 0 }}>
                <input type="checkbox" checked={f.enabled} disabled={busy || !onToggleFixing}
                  onChange={e => onToggleFixing?.(f.id, e.target.checked)} />
                <span style={{ fontSize: '0.9rem' }}>
                  {f.id} · {f.label || 'part'} · ({f.x.toFixed(0)}, {f.y.toFixed(0)})
                  {!f.enabled && <span className="muted"> · off</span>}
                </span>
              </label>
            ))}
          </div>
        </div>
      )}

      {showGcode && data.gcode && (
        <details style={{ marginTop: 8 }}>
          <summary className="muted" style={{ cursor: 'pointer' }}>G-code source</summary>
          <pre style={{
            maxHeight: 220, overflow: 'auto', fontSize: 11,
            background: '#0f1720', color: '#e2e8f0', padding: 12, borderRadius: 8,
          }}>{data.gcode.slice(0, 12000)}{data.gcode.length > 12000 ? '\n…' : ''}</pre>
        </details>
      )}
    </div>
  )
}
