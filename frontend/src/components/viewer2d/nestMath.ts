export type NestPlacement = {
  jobPartId?: number
  label?: string
  sheetIndex?: number
  x: number
  y: number
  width: number
  height: number
  nativeWidth?: number
  nativeHeight?: number
  rotationDeg?: number
  grainSensitive?: boolean
}

export function aabb(w: number, h: number, deg: number) {
  const r = (deg * Math.PI) / 180
  const c = Math.abs(Math.cos(r)), s = Math.abs(Math.sin(r))
  return { width: w * c + h * s, height: w * s + h * c }
}

export function constrainAngle(deg: number, grain: boolean) {
  let n = ((deg % 360) + 360) % 360
  if (!grain) return n
  const q = Math.round(n / 90) % 4
  return q === 1 || q === 3 ? 90 : 0
}

/** Snap within 8° of cardinal angles when not grain-locked. */
export function snapAngle(deg: number, grain: boolean) {
  const n = constrainAngle(deg, grain)
  if (grain) return n
  for (const t of [0, 90, 180, 270, 360]) {
    if (Math.abs(n - t) <= 8) return t % 360
  }
  return n
}

export function applyRotation(pl: NestPlacement, deg: number): NestPlacement {
  const nw = pl.nativeWidth || pl.width
  const nh = pl.nativeHeight || pl.height
  const rot = snapAngle(deg, !!pl.grainSensitive)
  const box = aabb(nw, nh, rot)
  return {
    ...pl,
    nativeWidth: nw,
    nativeHeight: nh,
    rotationDeg: rot,
    width: box.width,
    height: box.height,
  }
}

/** All instances of a part type share one orientation. */
export function syncOrientation(list: NestPlacement[], jobPartId: number, deg: number) {
  return list.map(p =>
    p.jobPartId === jobPartId ? applyRotation(p, deg) : p,
  )
}
