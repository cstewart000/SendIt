import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

export function LoginPage() {
  const { login } = useAuth()
  const nav = useNavigate()
  const [email, setEmail] = useState('hobby@sendit.local')
  const [password, setPassword] = useState('hobby12345')
  const [error, setError] = useState('')

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    try {
      await login(email, password)
      nav('/designs')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed')
    }
  }

  return (
    <div className="shell" style={{ maxWidth: 480, paddingTop: '8vh' }}>
      <div className="panel">
        <h1 className="brand" style={{ fontSize: '2.6rem', margin: '0 0 0.4rem' }}>SendIt</h1>
        <p className="muted">From DXF to LinuxCNC-ready timber parts.</p>
        <form className="grid" style={{ marginTop: '1.2rem' }} onSubmit={onSubmit}>
          <label>Email<input value={email} onChange={e => setEmail(e.target.value)} /></label>
          <label>Password<input type="password" value={password} onChange={e => setPassword(e.target.value)} /></label>
          {error && <p style={{ color: 'var(--danger)' }}>{error}</p>}
          <button type="submit">Sign in</button>
        </form>
        <p className="muted" style={{ marginTop: '1rem' }}>
          No account? <Link to="/register">Register</Link>
        </p>
      </div>
    </div>
  )
}
