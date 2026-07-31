import { useEffect, useRef } from 'react'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { assignTimberUVs, timberMaterials } from './timberMaterial'

type Pt = { x: number; y: number }
type Contour = { closed?: boolean; points: Pt[] }

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

/** Largest closed contour = outer; closed contours inside it become holes. */
function buildShape(contours: Contour[]): THREE.Shape | null {
  const closed = contours
    .filter(c => c.points && c.points.length >= 3 && c.closed !== false)
    .map(c => ({ pts: c.points, area: absArea(c.points) }))
    .filter(c => c.area > 1)
    .sort((a, b) => b.area - a.area)

  if (!closed.length) {
    const open = contours
      .filter(c => c.points && c.points.length >= 3)
      .map(c => ({ pts: c.points, area: absArea(c.points) }))
      .sort((a, b) => b.area - a.area)
    if (!open.length) return null
    const shape = new THREE.Shape()
    open[0].pts.forEach((p, i) => (i ? shape.lineTo(p.x, p.y) : shape.moveTo(p.x, p.y)))
    shape.closePath()
    return shape
  }

  const outer = closed[0]
  // THREE.Shape expects outer CCW; holes CW
  const oPts = signedArea(outer.pts) > 0 ? outer.pts : [...outer.pts].reverse()
  const shape = new THREE.Shape()
  oPts.forEach((p, i) => (i ? shape.lineTo(p.x, p.y) : shape.moveTo(p.x, p.y)))
  shape.closePath()

  for (let i = 1; i < closed.length; i++) {
    const h = closed[i]
    if (h.area >= outer.area * 0.98) continue
    if (!pointInPoly(centroid(h.pts), outer.pts)) continue
    const holePath = new THREE.Path()
    const hPts = signedArea(h.pts) > 0 ? [...h.pts].reverse() : h.pts
    hPts.forEach((p, i) => (i ? holePath.lineTo(p.x, p.y) : holePath.moveTo(p.x, p.y)))
    holePath.closePath()
    shape.holes.push(holePath)
  }
  return shape
}

export function Viewer3D({ contours = [], thickness = 18 }: { contours?: Contour[]; thickness?: number }) {
  const mount = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const el = mount.current
    if (!el) return
    const w = el.clientWidth || 480
    const h = 320
    const scene = new THREE.Scene()
    scene.background = new THREE.Color('#f4f1ea')
    const camera = new THREE.PerspectiveCamera(40, w / h, 0.1, 50000)
    const renderer = new THREE.WebGLRenderer({ antialias: true })
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2))
    renderer.setSize(w, h)
    renderer.outputColorSpace = THREE.SRGBColorSpace
    el.innerHTML = ''
    el.appendChild(renderer.domElement)

    const controls = new OrbitControls(camera, renderer.domElement)
    controls.enableDamping = true
    controls.dampingFactor = 0.08

    const hemi = new THREE.HemisphereLight(0xfff6e8, 0x6b5a48, 0.85)
    const key = new THREE.DirectionalLight(0xfff2dd, 1.35)
    key.position.set(180, 260, 120)
    const fill = new THREE.DirectionalLight(0xddeeff, 0.45)
    fill.position.set(-160, 80, -100)
    scene.add(hemi, key, fill)

    const mats = timberMaterials()
    let mesh: THREE.Mesh | null = null
    const shape = buildShape(contours)
    if (shape) {
      const geo = new THREE.ExtrudeGeometry(shape, {
        depth: Math.max(1, thickness),
        bevelEnabled: false,
        curveSegments: 12,
        steps: 1,
      })
      // ExtrudeGeometry groups: 0 = side walls, 1 = caps (top/bottom)
      assignTimberUVs(geo, 40)
      geo.center()
      // ExtrudeGeometry groups: 0 = caps (top/bottom), 1 = side walls
      mesh = new THREE.Mesh(geo, [mats.face, mats.edge])
      mesh.rotation.x = -Math.PI / 2
      scene.add(mesh)

      const box = new THREE.Box3().setFromObject(mesh)
      const size = box.getSize(new THREE.Vector3())
      const maxDim = Math.max(size.x, size.y, size.z, 40)
      const dist = maxDim * 1.35
      camera.position.set(dist * 0.75, dist * 0.55, dist * 0.75)
      controls.target.copy(box.getCenter(new THREE.Vector3()))
    } else {
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

    return () => {
      cancelAnimationFrame(raf)
      controls.dispose()
      if (mesh) {
        mesh.geometry.dispose()
        scene.remove(mesh)
      }
      mats.dispose()
      renderer.dispose()
      el.innerHTML = ''
    }
  }, [contours, thickness])

  return (
    <div
      ref={mount}
      style={{
        width: '100%',
        minHeight: 320,
        borderRadius: 8,
        overflow: 'hidden',
        border: '1px solid var(--line)',
        background: '#f4f1ea',
      }}
    />
  )
}
