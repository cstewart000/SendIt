import { useEffect, useRef } from 'react'

type Pt = { x: number; y: number }
type Contour = { id?: string; closed?: boolean; points: Pt[] }
type Issue = { highlight?: Pt[]; severity?: string }

type Props = {
  contours?: Contour[]
  issues?: Issue[]
  nests?: { x: number; y: number; width: number; height: number; label?: string; sheetIndex?: number }[]
  sheet?: { width: number; height: number }
}

export function Viewer2D({ contours = [], issues = [], nests, sheet }: Props) {
  const ref = useRef<HTMLCanvasElement>(null)

  useEffect(() => {
    const canvas = ref.current
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    if (!ctx) return
    const w = canvas.width, h = canvas.height
    ctx.clearRect(0, 0, w, h)
    // CAD-style: white sheet, black geometry
    ctx.fillStyle = '#ffffff'
    ctx.fillRect(0, 0, w, h)

    let minX = 0, minY = 0, maxX = sheet?.width || 100, maxY = sheet?.height || 100
    if (!sheet && contours.length) {
      minX = Infinity; minY = Infinity; maxX = -Infinity; maxY = -Infinity
      for (const c of contours) for (const p of c.points) {
        minX = Math.min(minX, p.x); minY = Math.min(minY, p.y)
        maxX = Math.max(maxX, p.x); maxY = Math.max(maxY, p.y)
      }
      if (!Number.isFinite(minX)) {
        minX = 0; minY = 0; maxX = 100; maxY = 100
      }
    }
    const pad = 24
    const sx = (w - pad * 2) / Math.max(1, maxX - minX)
    const sy = (h - pad * 2) / Math.max(1, maxY - minY)
    const s = Math.min(sx, sy)
    const tx = (p: Pt) => pad + (p.x - minX) * s
    const ty = (p: Pt) => h - pad - (p.y - minY) * s

    // Light grid
    ctx.strokeStyle = '#e8e8e8'
    ctx.lineWidth = 1
    const step = niceGridStep((maxX - minX) / 8)
    if (step > 0) {
      const x0 = Math.floor(minX / step) * step
      const y0 = Math.floor(minY / step) * step
      for (let x = x0; x <= maxX + step; x += step) {
        ctx.beginPath()
        ctx.moveTo(tx({ x, y: minY }), ty({ x, y: minY }))
        ctx.lineTo(tx({ x, y: maxY }), ty({ x, y: maxY }))
        ctx.stroke()
      }
      for (let y = y0; y <= maxY + step; y += step) {
        ctx.beginPath()
        ctx.moveTo(tx({ x: minX, y }), ty({ x: minX, y }))
        ctx.lineTo(tx({ x: maxX, y }), ty({ x: maxX, y }))
        ctx.stroke()
      }
    }

    if (sheet) {
      ctx.strokeStyle = '#333333'
      ctx.lineWidth = 1.25
      ctx.strokeRect(tx({ x: 0, y: 0 }), ty({ x: 0, y: sheet.height }), sheet.width * s, sheet.height * s)
    }

    ctx.strokeStyle = '#000000'
    ctx.lineWidth = 1.5
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
        const x = tx({ x: n.x, y: n.y }), y = ty({ x: n.x, y: n.y + n.height })
        ctx.fillRect(x, y, n.width * s, n.height * s)
        ctx.strokeRect(x, y, n.width * s, n.height * s)
        ctx.fillStyle = '#111111'
        ctx.font = '12px "IBM Plex Sans", Helvetica, Arial, sans-serif'
        ctx.fillText(n.label || 'Part', x + 4, y + 14)
      }
    }
  }, [contours, issues, nests, sheet])

  return (
    <canvas
      ref={ref}
      width={720}
      height={420}
      style={{
        width: '100%',
        borderRadius: 8,
        border: '1px solid var(--line)',
        background: '#ffffff',
      }}
    />
  )
}

function niceGridStep(raw: number): number {
  if (!Number.isFinite(raw) || raw <= 0) return 0
  const pow = Math.pow(10, Math.floor(Math.log10(raw)))
  const n = raw / pow
  const m = n < 1.5 ? 1 : n < 3.5 ? 2 : n < 7.5 ? 5 : 10
  return m * pow
}
