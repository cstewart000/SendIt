import { useEffect, useRef, useState, type PointerEvent } from 'react'
import { applyRotation, syncOrientation, type NestPlacement } from './nestMath'

type Props = {
  sheet: { width: number; height: number }
  placements: NestPlacement[]
  locked?: boolean
  onChange?: (next: NestPlacement[]) => void
}

type Drag =
  | { mode: 'move'; i: number; ox: number; oy: number; px: number; py: number }
  | { mode: 'rotate'; i: number; cx: number; cy: number; start: number; base: number }

export function NestEditor({ sheet, placements, locked, onChange }: Props) {
  const ref = useRef<HTMLCanvasElement>(null)
  const [sel, setSel] = useState<number | null>(null)
  const drag = useRef<Drag | null>(null)
  const view = useRef({ s: 1, pad: 24, h: 420 })

  useEffect(() => {
    const c = ref.current?.getContext('2d')
    const canvas = ref.current
    if (!c || !canvas) return
    const { width: W, height: H } = canvas
    const pad = 24
    const s = Math.min((W - pad * 2) / sheet.width, (H - pad * 2) / sheet.height)
    view.current = { s, pad, h: H }
    const tx = (x: number) => pad + x * s
    const ty = (y: number) => H - pad - y * s
    c.clearRect(0, 0, W, H)
    c.fillStyle = '#15110d'
    c.fillRect(0, 0, W, H)
    c.strokeStyle = '#6b5238'
    c.strokeRect(tx(0), ty(sheet.height), sheet.width * s, sheet.height * s)
    placements.filter(p => (p.sheetIndex || 0) === 0).forEach((n, i) => {
      const rot = ((n.rotationDeg || 0) * Math.PI) / 180
      const cx = n.x + n.width / 2, cy = n.y + n.height / 2
      const bw = n.nativeWidth || n.width, bh = n.nativeHeight || n.height
      c.save()
      c.translate(tx(cx), ty(cy))
      c.rotate(-rot)
      c.fillStyle = i === sel ? 'rgba(224,194,154,0.35)' : 'rgba(201,133,58,0.2)'
      c.strokeStyle = i === sel ? '#e0c29a' : '#c9853a'
      c.fillRect((-bw / 2) * s, (-bh / 2) * s, bw * s, bh * s)
      c.strokeRect((-bw / 2) * s, (-bh / 2) * s, bw * s, bh * s)
      c.fillStyle = '#f3ebe1'
      c.fillText(n.label || 'Part', (-bw / 2) * s + 4, (-bh / 2) * s + 14)
      if (i === sel && !locked) {
        c.beginPath()
        c.arc((bw / 2) * s, (-bh / 2) * s, 6, 0, Math.PI * 2)
        c.fillStyle = '#c9853a'
        c.fill()
      }
      c.restore()
    })
    console.log('[nest] draw', placements.length, 'sel', sel)
  }, [sheet, placements, sel, locked])

  function world(e: PointerEvent) {
    const canvas = ref.current!
    const r = canvas.getBoundingClientRect()
    const { s, pad, h } = view.current
    return {
      x: ((e.clientX - r.left) * (canvas.width / r.width) - pad) / s,
      y: (h - pad - (e.clientY - r.top) * (canvas.height / r.height)) / s,
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
    const p = list[i]
    return p.jobPartId != null ? syncOrientation(list, p.jobPartId, deg)
      : list.map((pl, j) => (j === i ? applyRotation(pl, deg) : pl))
  }

  function onDown(e: PointerEvent) {
    if (locked || !onChange) return
    const w = world(e)
    const i = hit(w.x, w.y)
    setSel(i)
    if (i == null) return
    const p = placements[i]
    const cx = p.x + p.width / 2, cy = p.y + p.height / 2
    const nw = p.nativeWidth || p.width, nh = p.nativeHeight || p.height
    const rot = ((p.rotationDeg || 0) * Math.PI) / 180
    const hx = cx + (nw / 2) * Math.cos(rot) - (-nh / 2) * Math.sin(rot)
    const hy = cy + (nw / 2) * Math.sin(rot) + (-nh / 2) * Math.cos(rot)
    drag.current = Math.hypot(w.x - hx, w.y - hy) < 12 / view.current.s
      ? { mode: 'rotate', i, cx, cy, start: Math.atan2(w.y - cy, w.x - cx), base: p.rotationDeg || 0 }
      : { mode: 'move', i, ox: w.x, oy: w.y, px: p.x, py: p.y }
    ;(e.target as HTMLElement).setPointerCapture(e.pointerId)
  }

  function onMove(e: PointerEvent) {
    if (!drag.current || !onChange) return
    const w = world(e), d = drag.current
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

  return (
    <div>
      <canvas ref={ref} width={720} height={420}
        style={{ width: '100%', borderRadius: 8, touchAction: 'none', cursor: locked ? 'default' : 'grab' }}
        onPointerDown={onDown} onPointerMove={onMove} onPointerUp={() => { drag.current = null }} />
      {!locked && (
        <div style={{ display: 'flex', gap: 8, marginTop: 8, flexWrap: 'wrap', alignItems: 'center' }}>
          <button type="button" className="ghost" disabled={sel == null} onClick={rotate90}>Rotate 90°</button>
          <span className="muted">Drag move · corner handle rotates (snaps ~90°) · shared per part type</span>
        </div>
      )}
    </div>
  )
}
