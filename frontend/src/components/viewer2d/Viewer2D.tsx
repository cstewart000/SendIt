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
    ctx.fillStyle = '#101826'
    ctx.fillRect(0, 0, w, h)

    let minX = 0, minY = 0, maxX = sheet?.width || 100, maxY = sheet?.height || 100
    if (!sheet && contours.length) {
      minX = Infinity; minY = Infinity; maxX = -Infinity; maxY = -Infinity
      for (const c of contours) for (const p of c.points) {
        minX = Math.min(minX, p.x); minY = Math.min(minY, p.y)
        maxX = Math.max(maxX, p.x); maxY = Math.max(maxY, p.y)
      }
    }
    const pad = 24
    const sx = (w - pad * 2) / Math.max(1, maxX - minX)
    const sy = (h - pad * 2) / Math.max(1, maxY - minY)
    const s = Math.min(sx, sy)
    const tx = (p: Pt) => pad + (p.x - minX) * s
    const ty = (p: Pt) => h - pad - (p.y - minY) * s

    if (sheet) {
      ctx.strokeStyle = '#3d4f66'
      ctx.strokeRect(tx({ x: 0, y: 0 }), ty({ x: 0, y: sheet.height }), sheet.width * s, sheet.height * s)
    }

    ctx.strokeStyle = '#7dd3fc'
    ctx.lineWidth = 1.5
    for (const c of contours) {
      if (!c.points?.length) continue
      ctx.beginPath()
      c.points.forEach((p, i) => i ? ctx.lineTo(tx(p), ty(p)) : ctx.moveTo(tx(p), ty(p)))
      if (c.closed) ctx.closePath()
      ctx.stroke()
    }

    for (const issue of issues) {
      if (!issue.highlight?.length) continue
      ctx.fillStyle = issue.severity === 'error' ? 'rgba(217,106,78,0.35)' : 'rgba(201,133,58,0.35)'
      ctx.beginPath()
      issue.highlight.forEach((p, i) => i ? ctx.lineTo(tx(p), ty(p)) : ctx.moveTo(tx(p), ty(p)))
      ctx.fill()
    }

    if (nests) {
      for (const n of nests.filter(x => (x.sheetIndex || 0) === 0)) {
        ctx.fillStyle = 'rgba(59,130,196,0.18)'
        ctx.strokeStyle = '#3b82c4'
        const x = tx({ x: n.x, y: n.y }), y = ty({ x: n.x, y: n.y + n.height })
        ctx.fillRect(x, y, n.width * s, n.height * s)
        ctx.strokeRect(x, y, n.width * s, n.height * s)
        ctx.fillStyle = '#e8eef5'
        ctx.font = '12px "IBM Plex Sans", Helvetica, Arial, sans-serif'
        ctx.fillText(n.label || 'Part', x + 4, y + 14)
      }
    }
    console.log('[viewer2d] draw contours=', contours.length, 'issues=', issues.length)
  }, [contours, issues, nests, sheet])

  return <canvas ref={ref} width={720} height={420} style={{ width: '100%', borderRadius: 8 }} />
}
