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
  c.fillStyle = selected ? 'rgba(224,194,154,0.35)' : 'rgba(201,133,58,0.22)'
  c.strokeStyle = selected ? '#e0c29a' : '#c9853a'
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

  c.fillStyle = '#f3ebe1'
  c.fillText(n.label || 'Part', (-nw / 2) * s + 4, (-nh / 2) * s + 14)
  if (showHandle) {
    c.beginPath()
    c.arc((nw / 2) * s, (-nh / 2) * s, 6, 0, Math.PI * 2)
    c.fillStyle = '#c9853a'
    c.fill()
  }
  c.restore()
}
