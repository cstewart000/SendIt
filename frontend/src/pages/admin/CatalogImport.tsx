import { useRef, useState } from 'react'
import { api } from '../../api/client'

type Result = { message: string; machinesCreated: number; toolsCreated: number; machineId?: number }

/** Upload LinuxCNC (.ini/.tbl) or Fusion 360 (.json) catalog files. */
export function CatalogImport({
  machineId, mode, onDone,
}: {
  machineId?: number | null
  mode: 'machine' | 'tools'
  onDone: (machineId?: number) => Promise<void>
}) {
  const input = useRef<HTMLInputElement>(null)
  const [busy, setBusy] = useState(false)
  const [msg, setMsg] = useState('')

  async function onPick(file: File | undefined) {
    if (!file) return
    if (mode === 'tools' && machineId == null) {
      setMsg('Select a machine first')
      return
    }
    setBusy(true)
    setMsg('')
    try {
      const fd = new FormData()
      fd.append('file', file)
      if (machineId != null) fd.append('machineId', String(machineId))
      const r = await api<Result>('/admin/import', { method: 'POST', body: fd })
      console.log('[admin] import', file.name, r)
      setMsg(r.message)
      await onDone(r.machineId)
    } catch (e) {
      setMsg(e instanceof Error ? e.message : 'Import failed')
      console.error('[admin] import failed', e)
    } finally {
      setBusy(false)
      if (input.current) input.current.value = ''
    }
  }

  const accept = mode === 'machine' ? '.ini,.json,.JSON' : '.tbl,.json,.JSON,.tools'
  return (
    <div className="grid" style={{ gap: '0.4rem' }}>
      <label className="muted" style={{ margin: 0 }}>
        Import {mode === 'machine' ? 'machine (.ini / Fusion JSON)' : 'tools (.tbl / Fusion JSON)'}
        <input ref={input} type="file" accept={accept} disabled={busy}
          onChange={e => onPick(e.target.files?.[0])} />
      </label>
      {msg && <p className="muted" style={{ margin: 0 }}>{msg}</p>}
    </div>
  )
}
