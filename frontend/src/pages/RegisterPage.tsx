import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

export function RegisterPage() {
  const { register } = useAuth()
  const nav = useNavigate()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    try {
      await register(email, password, name)
      nav('/designs')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Register failed')
    }
  }

  return (
    <div className="shell" style={{ maxWidth: 480, paddingTop: '8vh' }}>
      <div className="panel">
        <h1>Create account</h1>
        <form className="grid" onSubmit={onSubmit}>
          <label>Name<input value={name} onChange={e => setName(e.target.value)} /></label>
          <label>Email<input value={email} onChange={e => setEmail(e.target.value)} /></label>
          <label>Password<input type="password" value={password} onChange={e => setPassword(e.target.value)} /></label>
          {error && <p style={{ color: 'var(--danger)' }}>{error}</p>}
          <button type="submit">Register</button>
        </form>
        <p className="muted" style={{ marginTop: '1rem' }}><Link to="/login">Back to sign in</Link></p>
      </div>
    </div>
  )
}
