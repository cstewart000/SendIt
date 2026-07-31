import { useCallback, useEffect, useRef, useState, type PointerEvent, type WheelEvent as REWheelEvent } from 'react'
import { applyRotation, type NestPlacement } from './nestMath'
import { drawNestPart, type NestShape } from './nestDraw'

type Props = {
  sheet: { width: number; height: number }
  placements: NestPlacement[]
  shapes?: Record<number, NestShape>
  locked?: boolean
  onChange?: (next: NestPlacement[]) => void
}

type Drag =
  | { mode: 'move'; i: number; ox: number; oy: number; px: number; py: number }
  | { mode: 'rotate'; i: number; cx: number; cy: number; start: number; base: number }
  | { mode: 'pan'; x0: number; y0: number; ox: number; oy: number }

type View = { scale: number; ox: number; oy: number }

const W = 720
const H = 420
const PAD = 24

function fitSheet(sheet: { width: number; height: number }): View {
  const scale = Math.min((W - PAD * 2) / sheet.width, (H - PAD * 2) / sheet.height)
  return {
    scale,
    ox: PAD,
    oy: H - PAD,
  }
}

export function NestEditor({ sheet, placements, shapes = {}, locked, onChange }: Props) {
  const ref = useRef<HTMLCanvasElement>(null)
  const [sel, setSel] = useState<number | null>(null)
  const [view, setView] = useState<View>(() => fitSheet(sheet))
  const drag = useRef<Drag | null>(null)
  const spaceDown = useRef(false)
  const sheetKey = useRef(`${sheet.width}x${sheet.height}`)

  useEffect(() => {
    const k = `${sheet.width}x${sheet.height}`
    if (k !== sheetKey.current) {
      sheetKey.current = k
      setView(fitSheet(sheet))
    }
  }, [sheet])

  const draw = useCallback(() => {
    const c = ref.current?.getContext('2d')
    const canvas = ref.current
    if (!c || !canvas) return
    const { scale, ox, oy } = view
    const tx = (x: number) => ox + x * scale
    const ty = (y: number) => oy - y * scale

    c.clearRect(0, 0, W, H)
    c.fillStyle = '#ffffff'
    c.fillRect(0, 0, W, H)

    // Light grid
    const step = niceGridStep(Math.max(sheet.width, sheet.height) / 12)
    if (step > 0) {
      c.strokeStyle = '#e8e8e8'
      c.lineWidth = 1
      for (let x = 0; x <= sheet.width + 1e-6; x += step) {
        c.beginPath()
        c.moveTo(tx(x), ty(0))
        c.lineTo(tx(x), ty(sheet.height))
        c.stroke()
      }
      for (let y = 0; y <= sheet.height + 1e-6; y += step) {
        c.beginPath()
        c.moveTo(tx(0), ty(y))
        c.lineTo(tx(sheet.width), ty(y))
        c.stroke()
      }
    }

    c.strokeStyle = '#333333'
    c.lineWidth = 1.5
    c.strokeRect(tx(0), ty(sheet.height), sheet.width * scale, sheet.height * scale)

    placements.filter(p => (p.sheetIndex || 0) === 0).forEach((n, i) => {
      const shape = n.jobPartId != null ? shapes[n.jobPartId] : undefined
      drawNestPart(c, n, shape, scale, tx, ty, i === sel, i === sel && !locked)
    })
  }, [sheet, placements, shapes, sel, locked, view])

  useEffect(() => { draw() }, [draw])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.code === 'Space') {
        spaceDown.current = e.type === 'keydown'
        if (e.type === 'keydown') e.preventDefault()
      }
    }
    window.addEventListener('keydown', onKey)
    window.addEventListener('keyup', onKey)
    return () => {
      window.removeEventListener('keydown', onKey)
      window.removeEventListener('keyup', onKey)
    }
  }, [])

  function canvasXY(e: { clientX: number; clientY: number }) {
    const canvas = ref.current!
    const r = canvas.getBoundingClientRect()
    return {
      x: (e.clientX - r.left) * (canvas.width / r.width),
      y: (e.clientY - r.top) * (canvas.height / r.height),
    }
  }

  function world(e: PointerEvent) {
    const { x, y } = canvasXY(e)
    const { scale, ox, oy } = view
    return {
      x: (x - ox) / scale,
      y: (oy - y) / scale,
    }
  }

  function hit(wx: number, wy: number) {
    for (let i = placements.length - 1; i >= 0; i--) {
      const p = placements[i]
      if ((p.sheetIndex || 0) === 0
        && wx >= p.x && wx <= p.x + p.width && wy >= p.y && wy <= p.y + p.height) return i
    }
    return null
  }

  function setRot(list: NestPlacement[], i: number, deg: number) {
    return list.map((pl, j) => (j === i ? applyRotation(pl, deg) : pl))
  }

  function onWheel(e: REWheelEvent) {
    e.preventDefault()
    const { x, y } = canvasXY(e)
    const factor = e.deltaY < 0 ? 1.12 : 1 / 1.12
    setView(v => {
      const next = Math.min(50, Math.max(0.02, v.scale * factor))
      const k = next / v.scale
      return {
        scale: next,
        ox: x - (x - v.ox) * k,
        oy: y - (y - v.oy) * k,
      }
    })
  }

  function onDown(e: PointerEvent) {
    const { x, y } = canvasXY(e)
    const wantPan = e.button === 1 || e.button === 2 || spaceDown.current
      || (e.button === 0 && (locked || !onChange))
    if (wantPan && e.button !== 0 || wantPan && (e.button === 0 && (locked || spaceDown.current || !onChange))) {
      if (e.button === 2) e.preventDefault()
      drag.current = { mode: 'pan', x0: x, y0: y, ox: view.ox, oy: view.oy }
      ;(e.target as HTMLElement).setPointerCapture(e.pointerId)
      return
    }
    // Middle / right always pan
    if (e.button === 1 || e.button === 2) {
      e.preventDefault()
      drag.current = { mode: 'pan', x0: x, y0: y, ox: view.ox, oy: view.oy }
      ;(e.target as HTMLElement).setPointerCapture(e.pointerId)
      return
    }
    if (spaceDown.current) {
      drag.current = { mode: 'pan', x0: x, y0: y, ox: view.ox, oy: view.oy }
      ;(e.target as HTMLElement).setPointerCapture(e.pointerId)
      return
    }
    if (locked || !onChange) {
      drag.current = { mode: 'pan', x0: x, y0: y, ox: view.ox, oy: view.oy }
      ;(e.target as HTMLElement).setPointerCapture(e.pointerId)
      return
    }
    const w = world(e)
    const i = hit(w.x, w.y)
    setSel(i)
    if (i == null) {
      // Empty space: pan
      drag.current = { mode: 'pan', x0: x, y0: y, ox: view.ox, oy: view.oy }
      ;(e.target as HTMLElement).setPointerCapture(e.pointerId)
      return
    }
    const p = placements[i]
    const cx = p.x + p.width / 2, cy = p.y + p.height / 2
    const nw = p.nativeWidth || p.width, nh = p.nativeHeight || p.height
    const rot = ((p.rotationDeg || 0) * Math.PI) / 180
    const hx = cx + (nw / 2) * Math.cos(rot) - (-nh / 2) * Math.sin(rot)
    const hy = cy + (nw / 2) * Math.sin(rot) + (-nh / 2) * Math.cos(rot)
    drag.current = Math.hypot(w.x - hx, w.y - hy) < 12 / view.scale
      ? { mode: 'rotate', i, cx, cy, start: Math.atan2(w.y - cy, w.x - cx), base: p.rotationDeg || 0 }
      : { mode: 'move', i, ox: w.x, oy: w.y, px: p.x, py: p.y }
    ;(e.target as HTMLElement).setPointerCapture(e.pointerId)
  }

  function onMove(e: PointerEvent) {
    const d = drag.current
    if (!d) return
    if (d.mode === 'pan') {
      const { x, y } = canvasXY(e)
      setView(v => ({
        ...v,
        ox: d.ox + (x - d.x0),
        oy: d.oy + (y - d.y0),
      }))
      return
    }
    if (!onChange) return
    const w = world(e)
    if (d.mode === 'move') {
      onChange(placements.map((pl, i) =>
        i === d.i ? { ...pl, x: d.px + (w.x - d.ox), y: d.py + (w.y - d.oy) } : pl))
    } else {
      const deg = d.base + ((Math.atan2(w.y - d.cy, w.x - d.cx) - d.start) * 180) / Math.PI
      onChange(setRot(placements, d.i, deg))
    }
  }

  function rotate90() {
    if (sel == null || locked || !onChange) return
    onChange(setRot(placements, sel, (placements[sel].rotationDeg || 0) + 90))
  }

  function resetView() {
    setView(fitSheet(sheet))
  }

  return (
    <div>
      <canvas
        ref={ref}
        width={W}
        height={H}
        title="Scroll: zoom · Empty/right-drag: pan · Drag parts to move"
        style={{
          width: '100%',
          borderRadius: 8,
          border: '1px solid var(--line)',
          background: '#ffffff',
          touchAction: 'none',
          cursor: locked ? 'grab' : 'default',
        }}
        onWheel={onWheel}
        onPointerDown={onDown}
        onPointerMove={onMove}
        onPointerUp={() => { drag.current = null }}
        onPointerCancel={() => { drag.current = null }}
        onDoubleClick={resetView}
        onContextMenu={e => e.preventDefault()}
      />
      <div style={{ display: 'flex', gap: 8, marginTop: 8, flexWrap: 'wrap', alignItems: 'center' }}>
        {!locked && (
          <button type="button" className="ghost" disabled={sel == null} onClick={rotate90}>Rotate 90°</button>
        )}
        <button type="button" className="ghost" onClick={resetView}>Fit sheet</button>
        <span className="muted">
          Scroll zoom · drag empty / right-drag pan · drag part to move
        </span>
      </div>
    </div>
  )
}

function niceGridStep(raw: number): number {
  if (!Number.isFinite(raw) || raw <= 0) return 0
  const pow = Math.pow(10, Math.floor(Math.log10(raw)))
  const n = raw / pow
  const m = n < 1.5 ? 1 : n < 3.5 ? 2 : n < 7.5 ? 5 : 10
  return m * pow
}
