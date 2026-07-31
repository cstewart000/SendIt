import type { NestPlacement } from './nestMath'

export type NestShape = {
  jobPartId: number
  nativeWidth: number
  nativeHeight: number
  contours: { closed: boolean; points: { x: number; y: number }[] }[]
}

/** Draw real part outline (holes via evenodd) at placement pose. */
export function drawNestPart(
  c: CanvasRenderingContext2D,
  n: NestPlacement,
  shape: NestShape | undefined,
  s: number,
  tx: (x: number) => number,
  ty: (y: number) => number,
  selected: boolean,
  showHandle: boolean,
) {
  const rot = ((n.rotationDeg || 0) * Math.PI) / 180
  const cx = n.x + n.width / 2, cy = n.y + n.height / 2
  const nw = shape?.nativeWidth || n.nativeWidth || n.width
  const nh = shape?.nativeHeight || n.nativeHeight || n.height
  c.save()
  c.translate(tx(cx), ty(cy))
  c.rotate(-rot)
  c.fillStyle = selected ? 'rgba(125,211,252,0.28)' : 'rgba(59,130,196,0.18)'
  c.strokeStyle = selected ? '#7dd3fc' : '#3b82c4'
  c.lineWidth = selected ? 2 : 1.5

  if (shape?.contours?.length) {
    c.beginPath()
    for (const cont of shape.contours) {
      cont.points.forEach((p, i) => {
        const x = (p.x - nw / 2) * s
        const y = -(p.y - nh / 2) * s
        if (i === 0) c.moveTo(x, y)
        else c.lineTo(x, y)
      })
      if (cont.closed) c.closePath()
    }
    c.fill('evenodd')
    c.stroke()
  } else {
    c.fillRect((-nw / 2) * s, (-nh / 2) * s, nw * s, nh * s)
    c.strokeRect((-nw / 2) * s, (-nh / 2) * s, nw * s, nh * s)
  }

  c.fillStyle = '#e8eef5'
  c.font = '12px "IBM Plex Sans", Helvetica, Arial, sans-serif'
  c.fillText(n.label || 'Part', (-nw / 2) * s + 4, (-nh / 2) * s + 14)
  if (showHandle) {
    c.beginPath()
    c.arc((nw / 2) * s, (-nh / 2) * s, 6, 0, Math.PI * 2)
    c.fillStyle = '#2563a8'
    c.fill()
  }
  c.restore()
}
