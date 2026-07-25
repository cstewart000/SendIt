import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

export function Shell({ children }: { children: ReactNode }) {
  const { email, role, logout } = useAuth()
  return (
    <div className="shell">
      <nav className="nav">
        <Link to="/designs" className="brand">SendIt</Link>
        <div style={{ display: 'flex', gap: '0.8rem', alignItems: 'center' }}>
          <Link to="/designs">Designs</Link>
          <Link to="/jobs">Jobs</Link>
          {role === 'ADMIN' && <Link to="/admin">Admin</Link>}
          <span className="muted">{email}</span>
          <button className="ghost" onClick={logout}>Logout</button>
        </div>
      </nav>
      {children}
    </div>
  )
}
