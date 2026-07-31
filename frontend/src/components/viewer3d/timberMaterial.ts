import * as THREE from 'three'

/** Procedural plywood face grain (long grain along V). */
export function timberFaceTexture(size = 512): THREE.CanvasTexture {
  const canvas = document.createElement('canvas')
  canvas.width = size
  canvas.height = size
  const ctx = canvas.getContext('2d')!

  // Base board colour
  const base = ctx.createLinearGradient(0, 0, size, 0)
  base.addColorStop(0, '#d4a574')
  base.addColorStop(0.35, '#c9955c')
  base.addColorStop(0.55, '#dbb07a')
  base.addColorStop(1, '#c48a4e')
  ctx.fillStyle = base
  ctx.fillRect(0, 0, size, size)

  // Soft growth bands
  for (let i = 0; i < 28; i++) {
    const x = (i / 28) * size
    const w = 4 + (i % 4) * 3
    ctx.fillStyle = `rgba(120, 70, 30, ${0.04 + (i % 3) * 0.03})`
    ctx.fillRect(x, 0, w, size)
  }

  // Grain streaks (wavy)
  for (let i = 0; i < 70; i++) {
    const x0 = (i / 70) * size + Math.sin(i * 2.1) * 3
    const alpha = 0.1 + (i % 7) * 0.035
    ctx.strokeStyle = `rgba(${70 + (i % 20)}, ${40 + (i % 15)}, ${18 + (i % 10)}, ${alpha})`
    ctx.lineWidth = 0.8 + (i % 4) * 0.45
    ctx.beginPath()
    ctx.moveTo(x0, 0)
    for (let y = 0; y <= size; y += 4) {
      ctx.lineTo(x0 + Math.sin(y * 0.045 + i * 0.7) * 7 + Math.sin(y * 0.11) * 2, y)
    }
    ctx.stroke()
  }

  // Plywood pore / fleck noise
  for (let n = 0; n < 1200; n++) {
    ctx.fillStyle = `rgba(60, 35, 15, ${Math.random() * 0.12})`
    ctx.fillRect(Math.random() * size, Math.random() * size, 1 + Math.random() * 2, 1)
  }

  // Occasional darker mineral streak
  for (let k = 0; k < 3; k++) {
    const x = size * (0.2 + k * 0.28)
    ctx.strokeStyle = 'rgba(90, 50, 25, 0.25)'
    ctx.lineWidth = 2
    ctx.beginPath()
    ctx.moveTo(x, 0)
    for (let y = 0; y <= size; y += 6) {
      ctx.lineTo(x + Math.sin(y * 0.03 + k) * 12, y)
    }
    ctx.stroke()
  }

  const map = new THREE.CanvasTexture(canvas)
  map.colorSpace = THREE.SRGBColorSpace
  map.wrapS = map.wrapT = THREE.RepeatWrapping
  map.anisotropy = 8
  return map
}

/** End-grain style for extruded sides (growth rings). */
export function timberEdgeTexture(size = 256): THREE.CanvasTexture {
  const canvas = document.createElement('canvas')
  canvas.width = size
  canvas.height = size
  const ctx = canvas.getContext('2d')!
  ctx.fillStyle = '#b8894f'
  ctx.fillRect(0, 0, size, size)
  const cx = size * 0.45, cy = size * 0.5
  for (let r = 4; r < size; r += 5 + (r % 3)) {
    ctx.strokeStyle = `rgba(90, 50, 22, ${0.08 + (r % 7) * 0.02})`
    ctx.lineWidth = 1 + (r % 2)
    ctx.beginPath()
    ctx.ellipse(cx, cy, r * 0.9, r * 1.05, 0.15, 0, Math.PI * 2)
    ctx.stroke()
  }
  for (let n = 0; n < 200; n++) {
    ctx.fillStyle = `rgba(70, 40, 18, ${Math.random() * 0.1})`
    ctx.fillRect(Math.random() * size, Math.random() * size, 2, 2)
  }
  const map = new THREE.CanvasTexture(canvas)
  map.colorSpace = THREE.SRGBColorSpace
  map.wrapS = map.wrapT = THREE.RepeatWrapping
  return map
}

export function timberMaterials(): {
  face: THREE.MeshStandardMaterial
  edge: THREE.MeshStandardMaterial
  dispose: () => void
} {
  const faceMap = timberFaceTexture()
  const edgeMap = timberEdgeTexture()
  // ~40 mm of grain repeat in model units (mm)
  faceMap.repeat.set(1, 1)
  edgeMap.repeat.set(1, 1)

  const face = new THREE.MeshStandardMaterial({
    map: faceMap,
    color: 0xf0d2a8,
    roughness: 0.82,
    metalness: 0,
    side: THREE.DoubleSide,
  })
  const edge = new THREE.MeshStandardMaterial({
    map: edgeMap,
    color: 0xe2c08a,
    roughness: 0.9,
    metalness: 0,
    side: THREE.DoubleSide,
  })
  return {
    face,
    edge,
    dispose: () => {
      faceMap.dispose()
      edgeMap.dispose()
      face.dispose()
      edge.dispose()
    },
  }
}

/** Planar UVs so grain reads on caps and edges after extrude (shape XY, depth Z). */
export function assignTimberUVs(geometry: THREE.BufferGeometry, mmPerTile = 45) {
  geometry.computeVertexNormals()
  const pos = geometry.attributes.position
  const nrm = geometry.attributes.normal
  const uvs = new Float32Array(pos.count * 2)
  for (let i = 0; i < pos.count; i++) {
    const x = pos.getX(i)
    const y = pos.getY(i)
    const z = pos.getZ(i)
    const nx = Math.abs(nrm.getX(i))
    const ny = Math.abs(nrm.getY(i))
    const nz = Math.abs(nrm.getZ(i))
    let u: number, v: number
    if (nz >= nx && nz >= ny) {
      // Cap faces
      u = x / mmPerTile
      v = y / mmPerTile
    } else if (ny >= nx) {
      u = x / mmPerTile
      v = z / mmPerTile
    } else {
      u = y / mmPerTile
      v = z / mmPerTile
    }
    uvs[i * 2] = u
    uvs[i * 2 + 1] = v
  }
  geometry.setAttribute('uv', new THREE.BufferAttribute(uvs, 2))
}
