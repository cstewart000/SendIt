import { useCallback, useEffect, useRef, useState, type PointerEvent as REPointerEvent, type WheelEvent as REWheelEvent } from 'react'

type Pt = { x: number; y: number }
type Contour = { id?: string; closed?: boolean; points: Pt[] }
type Issue = { highlight?: Pt[]; severity?: string }

type Props = {
  contours?: Contour[]
  issues?: Issue[]
  nests?: { x: number; y: number; width: number; height: number; label?: string; sheetIndex?: number }[]
  sheet?: { width: number; height: number }
}

type View = { scale: number; ox: number; oy: number }

const PAD = 24
const W = 720
const H = 420

function contentBounds(
  contours: Contour[],
  sheet?: Props['sheet'],
  nests?: Props['nests'],
): { minX: number; minY: number; maxX: number; maxY: number } {
  if (sheet) return { minX: 0, minY: 0, maxX: sheet.width, maxY: sheet.height }
  let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity
  for (const c of contours) for (const p of c.points || []) {
    minX = Math.min(minX, p.x); minY = Math.min(minY, p.y)
    maxX = Math.max(maxX, p.x); maxY = Math.max(maxY, p.y)
  }
  if (nests) for (const n of nests) {
    minX = Math.min(minX, n.x); minY = Math.min(minY, n.y)
    maxX = Math.max(maxX, n.x + n.width); maxY = Math.max(maxY, n.y + n.height)
  }
  if (!Number.isFinite(minX)) return { minX: 0, minY: 0, maxX: 100, maxY: 100 }
  // pad empty span
  if (maxX - minX < 1e-6) { minX -= 50; maxX += 50 }
  if (maxY - minY < 1e-6) { minY -= 50; maxY += 50 }
  return { minX, minY, maxX, maxY }
}

function fitView(b: { minX: number; minY: number; maxX: number; maxY: number }): View {
  const sx = (W - PAD * 2) / Math.max(1, b.maxX - b.minX)
  const sy = (H - PAD * 2) / Math.max(1, b.maxY - b.minY)
  const scale = Math.min(sx, sy)
  return {
    scale,
    ox: PAD - b.minX * scale,
    oy: H - PAD + b.minY * scale,
  }
}

export function Viewer2D({ contours = [], issues = [], nests, sheet }: Props) {
  const ref = useRef<HTMLCanvasElement>(null)
  const bounds = contentBounds(contours, sheet, nests)
  const [view, setView] = useState<View>(() => fitView(bounds))
  const pan = useRef<{ x0: number; y0: number; ox: number; oy: number } | null>(null)
  const spaceDown = useRef(false)
  const contentKey = useRef('')

  // Refit when geometry identity changes
  useEffect(() => {
    const key = JSON.stringify({
      n: contours.length,
      sheet,
      pts: contours.reduce((a, c) => a + (c.points?.length || 0), 0),
      nest: nests?.length || 0,
    })
    if (key !== contentKey.current) {
      contentKey.current = key
      setView(fitView(contentBounds(contours, sheet, nests)))
    }
  }, [contours, sheet, nests])

  const draw = useCallback(() => {
    const canvas = ref.current
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    if (!ctx) return
    const { scale, ox, oy } = view
    const tx = (p: Pt) => ox + p.x * scale
    const ty = (p: Pt) => oy - p.y * scale

    ctx.clearRect(0, 0, W, H)
    ctx.fillStyle = '#ffffff'
    ctx.fillRect(0, 0, W, H)

    // Grid in world space
    const worldLeft = (0 - ox) / scale
    const worldRight = (W - ox) / scale
    const worldBottom = (oy - H) / scale
    const worldTop = (oy - 0) / scale
    const span = Math.max(worldRight - worldLeft, worldTop - worldBottom)
    const step = niceGridStep(span / 10)
    if (step > 0) {
      ctx.strokeStyle = '#e8e8e8'
      ctx.lineWidth = 1
      const x0 = Math.floor(worldLeft / step) * step
      const y0 = Math.floor(worldBottom / step) * step
      for (let x = x0; x <= worldRight + step; x += step) {
        ctx.beginPath()
        ctx.moveTo(tx({ x, y: worldBottom }), ty({ x, y: worldBottom }))
        ctx.lineTo(tx({ x, y: worldTop }), ty({ x, y: worldTop }))
        ctx.stroke()
      }
      for (let y = y0; y <= worldTop + step; y += step) {
        ctx.beginPath()
        ctx.moveTo(tx({ x: worldLeft, y }), ty({ x: worldLeft, y }))
        ctx.lineTo(tx({ x: worldRight, y }), ty({ x: worldRight, y }))
        ctx.stroke()
      }
    }

    if (sheet) {
      ctx.strokeStyle = '#333333'
      ctx.lineWidth = 1.25
      ctx.strokeRect(
        tx({ x: 0, y: 0 }),
        ty({ x: 0, y: sheet.height }),
        sheet.width * scale,
        sheet.height * scale,
      )
    }

    ctx.strokeStyle = '#000000'
    ctx.lineWidth = Math.max(1, 1.5)
    ctx.lineJoin = 'round'
    ctx.lineCap = 'round'
    for (const c of contours) {
      if (!c.points?.length) continue
      ctx.beginPath()
      c.points.forEach((p, i) => (i ? ctx.lineTo(tx(p), ty(p)) : ctx.moveTo(tx(p), ty(p))))
      if (c.closed) ctx.closePath()
      ctx.stroke()
    }

    for (const issue of issues) {
      if (!issue.highlight?.length) continue
      ctx.fillStyle = issue.severity === 'error' ? 'rgba(180, 35, 24, 0.22)' : 'rgba(180, 120, 20, 0.22)'
      ctx.strokeStyle = issue.severity === 'error' ? '#b42318' : '#a86a10'
      ctx.lineWidth = 1.25
      ctx.beginPath()
      issue.highlight.forEach((p, i) => (i ? ctx.lineTo(tx(p), ty(p)) : ctx.moveTo(tx(p), ty(p))))
      ctx.closePath()
      ctx.fill()
      ctx.stroke()
    }

    if (nests) {
      for (const n of nests.filter(x => (x.sheetIndex || 0) === 0)) {
        ctx.fillStyle = 'rgba(0, 0, 0, 0.04)'
        ctx.strokeStyle = '#000000'
        ctx.lineWidth = 1
        const x = tx({ x: n.x, y: n.y })
        const y = ty({ x: n.x, y: n.y + n.height })
        ctx.fillRect(x, y, n.width * scale, n.height * scale)
        ctx.strokeRect(x, y, n.width * scale, n.height * scale)
        ctx.fillStyle = '#111111'
        ctx.font = '12px "IBM Plex Sans", Helvetica, Arial, sans-serif'
        ctx.fillText(n.label || 'Part', x + 4, y + 14)
      }
    }
  }, [view, contours, issues, nests, sheet])

  useEffect(() => { draw() }, [draw])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.code === 'Space') { spaceDown.current = e.type === 'keydown'; e.preventDefault() }
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

  function onWheel(e: REWheelEvent) {
    e.preventDefault()
    const { x, y } = canvasXY(e)
    const factor = e.deltaY < 0 ? 1.12 : 1 / 1.12
    setView(v => {
      const next = Math.min(200, Math.max(0.02, v.scale * factor))
      const k = next / v.scale
      // Zoom toward cursor
      return {
        scale: next,
        ox: x - (x - v.ox) * k,
        oy: y - (y - v.oy) * k,
      }
    })
  }

  function onPointerDown(e: REPointerEvent) {
    const panButton = e.button === 1 || e.button === 2 || spaceDown.current || e.button === 0
    if (!panButton) return
    // Left-drag always pans in plan view (no part editing here)
    if (e.button === 2) e.preventDefault()
    const { x, y } = canvasXY(e)
    pan.current = { x0: x, y0: y, ox: view.ox, oy: view.oy }
    ;(e.target as HTMLElement).setPointerCapture(e.pointerId)
  }

  function onPointerMove(e: REPointerEvent) {
    if (!pan.current) return
    const { x, y } = canvasXY(e)
    const d = pan.current
    setView(v => ({
      ...v,
      ox: d.ox + (x - d.x0),
      oy: d.oy + (y - d.y0),
    }))
  }

  function onPointerUp() {
    pan.current = null
  }

  function resetView() {
    setView(fitView(contentBounds(contours, sheet, nests)))
  }

  return (
    <div>
      <canvas
        ref={ref}
        width={W}
        height={H}
        title="Drag: pan · Scroll: zoom · Double-click: fit"
        onWheel={onWheel}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerCancel={onPointerUp}
        onDoubleClick={resetView}
        onContextMenu={e => e.preventDefault()}
        style={{
          width: '100%',
          borderRadius: 8,
          border: '1px solid var(--line)',
          background: '#ffffff',
          touchAction: 'none',
          cursor: pan.current ? 'grabbing' : 'grab',
        }}
      />
      <p className="muted" style={{ margin: '0.35rem 0 0', fontSize: '0.82rem' }}>
        Plan: drag pan · scroll zoom · double-click fit
      </p>
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
