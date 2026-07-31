import * as THREE from 'three'

/** Procedural oak grain for extruded timber meshes. */
export function timberMaterial(): THREE.MeshStandardMaterial {
  const size = 256
  const canvas = document.createElement('canvas')
  canvas.width = size
  canvas.height = size
  const ctx = canvas.getContext('2d')!
  ctx.fillStyle = '#c4a574'
  ctx.fillRect(0, 0, size, size)
  for (let i = 0; i < 48; i++) {
    const x = (i / 48) * size + Math.sin(i * 1.7) * 4
    ctx.strokeStyle = `rgba(110, 72, 36, ${0.12 + (i % 5) * 0.04})`
    ctx.lineWidth = 1 + (i % 3)
    ctx.beginPath()
    ctx.moveTo(x, 0)
    for (let y = 0; y <= size; y += 8) {
      ctx.lineTo(x + Math.sin(y * 0.08 + i) * 6, y)
    }
    ctx.stroke()
  }
  for (let n = 0; n < 400; n++) {
    ctx.fillStyle = `rgba(90, 55, 28, ${Math.random() * 0.08})`
    ctx.fillRect(Math.random() * size, Math.random() * size, 2, 2)
  }
  const map = new THREE.CanvasTexture(canvas)
  map.wrapS = map.wrapT = THREE.RepeatWrapping
  map.repeat.set(2, 2)
  console.log('[timber] grain texture ready')
  return new THREE.MeshStandardMaterial({
    map, color: '#e0c49a', roughness: 0.75, metalness: 0,
  })
}
