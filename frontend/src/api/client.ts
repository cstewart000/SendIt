const BASE = import.meta.env.VITE_API_BASE_URL || '/api/v1'

function token() {
  return localStorage.getItem('tn_token') || ''
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers || {})
  if (!(init.body instanceof FormData)) headers.set('Content-Type', 'application/json')
  const t = token()
  if (t) headers.set('Authorization', `Bearer ${t}`)
  console.log('[api]', init.method || 'GET', path)
  const res = await fetch(`${BASE}${path}`, { ...init, headers })
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: res.statusText }))
    console.error('[api] fail', path, err)
    throw new Error(err.error || res.statusText)
  }
  if (res.status === 204) return undefined as T
  const ct = res.headers.get('content-type') || ''
  if (ct.includes('application/json')) return res.json()
  return res.blob() as Promise<T>
}

export async function apiBlob(path: string): Promise<Blob> {
  const headers = new Headers()
  const t = token()
  if (t) headers.set('Authorization', `Bearer ${t}`)
  console.log('[api] blob', path)
  const res = await fetch(`${BASE}${path}`, { headers })
  if (!res.ok) throw new Error('Download failed')
  return res.blob()
}
