import { useEffect, useRef } from 'react'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { assignTimberUVs, timberMaterials } from './timberMaterial'

export type ViewerContour = { id?: string; closed?: boolean; points: { x: number; y: number }[] }
export type ViewerPart = {
  label?: string
  geometry?: { contours?: ViewerContour[] }
  contours?: ViewerContour[]
}

type Pt = { x: number; y: number }

function signedArea(pts: Pt[]): number {
  let a = 0
  for (let i = 0; i < pts.length; i++) {
    const p = pts[i], q = pts[(i + 1) % pts.length]
    a += p.x * q.y - q.x * p.y
  }
  return a / 2
}

function absArea(pts: Pt[]): number {
  return Math.abs(signedArea(pts))
}

function centroid(pts: Pt[]): Pt {
  let x = 0, y = 0
  for (const p of pts) { x += p.x; y += p.y }
  return { x: x / pts.length, y: y / pts.length }
}

function pointInPoly(p: Pt, poly: Pt[]): boolean {
  let inside = false
  for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
    const a = poly[i], b = poly[j]
    if (((a.y > p.y) !== (b.y > p.y))
      && (p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y + 1e-12) + a.x)) {
      inside = !inside
    }
  }
  return inside
}

function dist(a: Pt, b: Pt) {
  return Math.hypot(a.x - b.x, a.y - b.y)
}

function joinOpenContours(contours: ViewerContour[], eps: number): ViewerContour[] {
  const closed: ViewerContour[] = []
  const open: Pt[][] = []
  for (const c of contours) {
    if (!c.points || c.points.length < 2) continue
    const pts = c.points.map(p => ({ x: p.x, y: p.y }))
    if (c.closed && pts.length >= 3) {
      closed.push({ closed: true, points: pts })
      continue
    }
    if (pts.length >= 3 && dist(pts[0], pts[pts.length - 1]) <= eps * 2) {
      pts[pts.length - 1] = { ...pts[0] }
      closed.push({ closed: true, points: pts })
      continue
    }
    open.push(pts)
  }

  let changed = true
  while (changed) {
    changed = false
    outer:
    for (let i = 0; i < open.length; i++) {
      for (let j = i + 1; j < open.length; j++) {
        const a = open[i], b = open[j]
        const a0 = a[0], a1 = a[a.length - 1]
        const b0 = b[0], b1 = b[b.length - 1]
        let merged: Pt[] | null = null
        if (dist(a1, b0) <= eps) merged = [...a, ...b.slice(1)]
        else if (dist(a1, b1) <= eps) merged = [...a, ...[...b].reverse().slice(1)]
        else if (dist(a0, b1) <= eps) merged = [...b, ...a.slice(1)]
        else if (dist(a0, b0) <= eps) merged = [...[...b].reverse(), ...a.slice(1)]
        if (!merged) continue
        if (dist(merged[0], merged[merged.length - 1]) <= eps * 2) {
          merged[merged.length - 1] = { ...merged[0] }
          closed.push({ closed: true, points: merged })
          open.splice(j, 1)
          open.splice(i, 1)
        } else {
          open[i] = merged
          open.splice(j, 1)
        }
        changed = true
        break outer
      }
    }
  }
  for (const pts of open) {
    if (pts.length >= 3 && dist(pts[0], pts[pts.length - 1]) <= eps * 4) {
      pts[pts.length - 1] = { ...pts[0] }
      closed.push({ closed: true, points: pts })
    }
  }
  return closed.length ? closed : contours
}

function circularity(pts: Pt[]): number {
  const area = absArea(pts)
  let per = 0
  for (let i = 0; i < pts.length; i++) per += dist(pts[i], pts[(i + 1) % pts.length])
  if (per < 1e-9) return 0
  return (4 * Math.PI * area) / (per * per)
}

type Solid = { outer: Pt[]; holes: Pt[][] }

/** Split contours into independent solids (each outer + its holes). */
function extractSolids(raw: ViewerContour[]): Solid[] {
  if (!raw?.length) return []
  let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity
  for (const c of raw) for (const p of c.points || []) {
    minX = Math.min(minX, p.x); minY = Math.min(minY, p.y)
    maxX = Math.max(maxX, p.x); maxY = Math.max(maxY, p.y)
  }
  const diag = Math.hypot(Math.max(0, maxX - minX), Math.max(0, maxY - minY)) || 100
  const eps = Math.max(0.3, Math.min(3, diag * 0.002))
  const contours = joinOpenContours(raw, eps)

  type Ring = { pts: Pt[]; area: number; circ: number }
  const closed: Ring[] = contours
    .filter(c => c.points && c.points.length >= 3
      && (c.closed || dist(c.points[0], c.points[c.points.length - 1]) < eps * 4))
    .map(c => ({ pts: c.points, area: absArea(c.points), circ: circularity(c.points) }))
    .filter(c => c.area > 1)
    .sort((a, b) => b.area - a.area)

  if (!closed.length) {
    const open = contours
      .filter(c => c.points && c.points.length >= 3)
      .map(c => ({ pts: c.points as Pt[], area: absArea(c.points) }))
      .sort((a, b) => b.area - a.area)
    if (!open.length) return []
    return [{ outer: open[0].pts, holes: [] }]
  }

  const parent = closed.map(() => -1)
  const isHole = closed.map(() => false)
  for (let i = 0; i < closed.length; i++) {
    for (let j = 0; j < closed.length; j++) {
      if (i === j) continue
      if (closed[j].area <= closed[i].area) continue
      if (!pointInPoly(centroid(closed[i].pts), closed[j].pts)) continue
      // Nearest (smallest) containing ring is parent
      if (parent[i] < 0 || closed[j].area < closed[parent[i]].area) {
        parent[i] = j
        isHole[i] = true
      }
    }
  }

  const solids: Solid[] = []
  for (let i = 0; i < closed.length; i++) {
    if (isHole[i]) continue
    // Skip near-circles that are likely floating holes without a parent plate
    // only when a larger non-circle solid exists and this circle is tiny relative to model
    const holes: Pt[][] = []
    for (let h = 0; h < closed.length; h++) {
      if (parent[h] === i) holes.push(closed[h].pts)
    }
    solids.push({ outer: closed[i].pts, holes })
  }
  return solids
}

function solidToShape(solid: Solid): THREE.Shape {
  const oPts = signedArea(solid.outer) > 0 ? solid.outer : [...solid.outer].reverse()
  const shape = new THREE.Shape()
  oPts.forEach((p, i) => (i ? shape.lineTo(p.x, p.y) : shape.moveTo(p.x, p.y)))
  shape.closePath()
  for (const hole of solid.holes) {
    if (hole.length < 3) continue
    const holePath = new THREE.Path()
    const hPts = signedArea(hole) > 0 ? [...hole].reverse() : hole
    hPts.forEach((p, i) => (i ? holePath.lineTo(p.x, p.y) : holePath.moveTo(p.x, p.y)))
    holePath.closePath()
    shape.holes.push(holePath)
  }
  return shape
}

function contoursOfPart(p: ViewerPart): ViewerContour[] {
  return p.geometry?.contours || p.contours || []
}

type Props = {
  /** Full design contours (used when parts not provided). */
  contours?: ViewerContour[]
  /** Explicit design parts — each becomes its own 3D solid. */
  parts?: ViewerPart[]
  thickness?: number
}

export function Viewer3D({ contours = [], parts, thickness = 18 }: Props) {
  const mount = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const el = mount.current
    if (!el) return
    const w = el.clientWidth || 480
    const h = Math.max(el.clientHeight || 0, 360)
    const scene = new THREE.Scene()
    scene.background = new THREE.Color('#f4f1ea')
    const camera = new THREE.PerspectiveCamera(40, w / h, 0.1, 100000)
    const renderer = new THREE.WebGLRenderer({ antialias: true })
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2))
    renderer.setSize(w, h)
    renderer.outputColorSpace = THREE.SRGBColorSpace
    el.innerHTML = ''
    el.appendChild(renderer.domElement)

    const controls = new OrbitControls(camera, renderer.domElement)
    controls.enableDamping = true
    controls.dampingFactor = 0.08
    controls.enablePan = true
    controls.enableZoom = true
    controls.enableRotate = true
    controls.screenSpacePanning = true
    controls.zoomSpeed = 1.1
    controls.panSpeed = 0.9
    controls.rotateSpeed = 0.7
    controls.minDistance = 5
    controls.maxDistance = 50000
    // LMB rotate, MMB/wheel zoom, RMB pan (also: two-finger / trackpad)
    controls.mouseButtons = {
      LEFT: THREE.MOUSE.ROTATE,
      MIDDLE: THREE.MOUSE.DOLLY,
      RIGHT: THREE.MOUSE.PAN,
    }
    controls.touches = {
      ONE: THREE.TOUCH.ROTATE,
      TWO: THREE.TOUCH.DOLLY_PAN,
    }
    // Prevent browser context menu so right-drag pan works
    const blockCtx = (e: Event) => e.preventDefault()
    renderer.domElement.addEventListener('contextmenu', blockCtx)

    const hemi = new THREE.HemisphereLight(0xfff6e8, 0x6b5a48, 0.85)
    const key = new THREE.DirectionalLight(0xfff2dd, 1.35)
    key.position.set(180, 260, 120)
    const fill = new THREE.DirectionalLight(0xddeeff, 0.45)
    fill.position.set(-160, 80, -100)
    scene.add(hemi, key, fill)

    const mats = timberMaterials()
    const meshes: THREE.Mesh[] = []
    const group = new THREE.Group()

    // Prefer explicit parts; else split full contour set into solids
    let solids: Solid[] = []
    if (parts && parts.length) {
      for (const p of parts) {
        const cs = contoursOfPart(p)
        if (!cs.length) continue
        const extracted = extractSolids(cs)
        // Each design part should be one solid (its outer + holes); if multiple, keep all
        solids.push(...extracted)
      }
    } else {
      solids = extractSolids(contours)
    }

    const depth = Math.max(1, thickness)
    for (const solid of solids) {
      try {
        const shape = solidToShape(solid)
        const geo = new THREE.ExtrudeGeometry(shape, {
          depth,
          bevelEnabled: false,
          curveSegments: 12,
          steps: 1,
        })
        assignTimberUVs(geo, 40)
        // Keep DXF coordinates so multi-part layout matches 2D
        const mesh = new THREE.Mesh(geo, [mats.face, mats.edge])
        mesh.rotation.x = -Math.PI / 2
        group.add(mesh)
        meshes.push(mesh)
      } catch {
        // skip bad solid
      }
    }

    if (meshes.length) {
      // Center the whole assembly for comfortable orbit
      const box = new THREE.Box3().setFromObject(group)
      const center = box.getCenter(new THREE.Vector3())
      group.position.sub(center)
      scene.add(group)

      const size = box.getSize(new THREE.Vector3())
      const maxDim = Math.max(size.x, size.y, size.z, 40)
      const distCam = maxDim * 1.5
      camera.position.set(distCam * 0.75, distCam * 0.55, distCam * 0.75)
      controls.target.set(0, 0, 0)
      controls.update()
    } else {
      scene.add(group)
      camera.position.set(120, 100, 120)
      controls.target.set(0, 0, 0)
    }
    camera.lookAt(controls.target)

    let raf = 0
    const animate = () => {
      controls.update()
      renderer.render(scene, camera)
      raf = requestAnimationFrame(animate)
    }
    animate()

    const onResize = () => {
      if (!mount.current) return
      const nw = mount.current.clientWidth || w
      const nh = Math.max(mount.current.clientHeight || 0, 360)
      camera.aspect = nw / nh
      camera.updateProjectionMatrix()
      renderer.setSize(nw, nh)
    }
    window.addEventListener('resize', onResize)

    return () => {
      cancelAnimationFrame(raf)
      window.removeEventListener('resize', onResize)
      renderer.domElement.removeEventListener('contextmenu', blockCtx)
      controls.dispose()
      for (const m of meshes) {
        m.geometry.dispose()
        group.remove(m)
      }
      mats.dispose()
      renderer.dispose()
      el.innerHTML = ''
    }
  }, [contours, parts, thickness])

  return (
    <div
      ref={mount}
      title="Drag: rotate · Right-drag / two-finger: pan · Scroll: zoom"
      style={{
        width: '100%',
        minHeight: 360,
        height: 360,
        borderRadius: 8,
        overflow: 'hidden',
        border: '1px solid var(--line)',
        background: '#f4f1ea',
        touchAction: 'none',
        cursor: 'grab',
      }}
    />
  )
}
