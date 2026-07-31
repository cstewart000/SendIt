import { useEffect, useRef } from 'react'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { timberMaterial } from './timberMaterial'

type Pt = { x: number; y: number }
type Contour = { closed?: boolean; points: Pt[] }

export function Viewer3D({ contours = [], thickness = 18 }: { contours?: Contour[]; thickness?: number }) {
  const mount = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const el = mount.current
    if (!el) return
    const w = el.clientWidth || 480, h = 320
    const scene = new THREE.Scene()
    scene.background = new THREE.Color('#ffffff')
    const camera = new THREE.PerspectiveCamera(40, w / h, 0.1, 10000)
    const renderer = new THREE.WebGLRenderer({ antialias: true })
    renderer.setSize(w, h)
    el.innerHTML = ''
    el.appendChild(renderer.domElement)

    const controls = new OrbitControls(camera, renderer.domElement)
    controls.enableDamping = true
    controls.dampingFactor = 0.08

    const key = new THREE.DirectionalLight(0xfff5e8, 1.2)
    key.position.set(2, 4, 3)
    scene.add(key, new THREE.AmbientLight(0xfff8f0, 0.7))

    const shape = new THREE.Shape()
    const c = contours.find(x => x.closed && x.points.length > 2) || contours[0]
    if (c?.points?.length) {
      c.points.forEach((p, i) => i ? shape.lineTo(p.x, p.y) : shape.moveTo(p.x, p.y))
      shape.closePath()
      const geo = new THREE.ExtrudeGeometry(shape, { depth: thickness, bevelEnabled: false })
      geo.center()
      const mesh = new THREE.Mesh(geo, timberMaterial())
      mesh.rotation.x = -Math.PI / 2
      scene.add(mesh)
      const size = new THREE.Box3().setFromObject(mesh).getSize(new THREE.Vector3()).length()
      camera.position.set(size * 0.7, size * 0.6, size * 0.7)
    } else {
      camera.position.set(120, 100, 120)
    }
    camera.lookAt(0, 0, 0)
    controls.target.set(0, 0, 0)

    let raf = 0
    const animate = () => {
      controls.update()
      renderer.render(scene, camera)
      raf = requestAnimationFrame(animate)
    }
    animate()
    console.log('[viewer3d] orbit timber contours=', contours.length)
    return () => {
      cancelAnimationFrame(raf)
      controls.dispose()
      renderer.dispose()
      el.innerHTML = ''
    }
  }, [contours, thickness])

  return <div ref={mount} style={{ width: '100%', minHeight: 320, borderRadius: 8, overflow: 'hidden' }} />
}
