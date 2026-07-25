import { useEffect, useRef } from 'react'
import * as THREE from 'three'

type Pt = { x: number; y: number }
type Contour = { closed?: boolean; points: Pt[] }

export function Viewer3D({ contours = [], thickness = 18 }: { contours?: Contour[]; thickness?: number }) {
  const mount = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const el = mount.current
    if (!el) return
    const w = el.clientWidth || 480, h = 320
    const scene = new THREE.Scene()
    scene.background = new THREE.Color('#17120e')
    const camera = new THREE.PerspectiveCamera(40, w / h, 0.1, 10000)
    const renderer = new THREE.WebGLRenderer({ antialias: true })
    renderer.setSize(w, h)
    el.innerHTML = ''
    el.appendChild(renderer.domElement)

    const light = new THREE.DirectionalLight(0xffe0b8, 1.2)
    light.position.set(2, 4, 3)
    scene.add(light, new THREE.AmbientLight(0x665544, 0.7))

    const shape = new THREE.Shape()
    const c = contours.find(x => x.closed && x.points.length > 2) || contours[0]
    if (c?.points?.length) {
      c.points.forEach((p, i) => i ? shape.lineTo(p.x, p.y) : shape.moveTo(p.x, p.y))
      shape.closePath()
      const geo = new THREE.ExtrudeGeometry(shape, { depth: thickness, bevelEnabled: false })
      geo.center()
      const mat = new THREE.MeshStandardMaterial({ color: '#c9853a', roughness: 0.75, metalness: 0.05 })
      const mesh = new THREE.Mesh(geo, mat)
      mesh.rotation.x = -Math.PI / 2
      scene.add(mesh)
      const box = new THREE.Box3().setFromObject(mesh)
      const size = box.getSize(new THREE.Vector3()).length()
      camera.position.set(size * 0.7, size * 0.6, size * 0.7)
      camera.lookAt(0, 0, 0)
    } else {
      camera.position.set(120, 100, 120)
      camera.lookAt(0, 0, 0)
    }

    let frame = 0
    let raf = 0
    const animate = () => {
      frame++
      scene.rotation.y = Math.min(frame / 60, 1) * 0.35 + frame * 0.002
      renderer.render(scene, camera)
      raf = requestAnimationFrame(animate)
    }
    animate()
    console.log('[viewer3d] mounted contours=', contours.length)
    return () => {
      cancelAnimationFrame(raf)
      renderer.dispose()
      el.innerHTML = ''
    }
  }, [contours, thickness])

  return <div ref={mount} style={{ width: '100%', minHeight: 320, borderRadius: 8, overflow: 'hidden' }} />
}
