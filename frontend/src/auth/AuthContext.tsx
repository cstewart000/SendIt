import { createContext, useContext, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { api } from '../api/client'

type AuthState = {
  token: string | null
  email: string | null
  role: string | null
  name: string | null
  login: (email: string, password: string) => Promise<void>
  register: (email: string, password: string, name: string) => Promise<void>
  logout: () => void
}

const Ctx = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState(localStorage.getItem('tn_token'))
  const [email, setEmail] = useState(localStorage.getItem('tn_email'))
  const [role, setRole] = useState(localStorage.getItem('tn_role'))
  const [name, setName] = useState(localStorage.getItem('tn_name'))

  const persist = (data: { token: string; email: string; role: string; name?: string }) => {
    localStorage.setItem('tn_token', data.token)
    localStorage.setItem('tn_email', data.email)
    localStorage.setItem('tn_role', data.role)
    localStorage.setItem('tn_name', data.name || '')
    setToken(data.token); setEmail(data.email); setRole(data.role); setName(data.name || null)
    console.log('[auth] session', data.email, data.role)
  }

  const value = useMemo<AuthState>(() => ({
    token, email, role, name,
    async login(email, password) {
      const data = await api<{ token: string; email: string; role: string; name: string }>(
        '/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) })
      persist(data)
    },
    async register(email, password, name) {
      const data = await api<{ token: string; email: string; role: string; name: string }>(
        '/auth/register', { method: 'POST', body: JSON.stringify({ email, password, name }) })
      persist(data)
    },
    logout() {
      localStorage.clear()
      setToken(null); setEmail(null); setRole(null); setName(null)
      console.log('[auth] logout')
    },
  }), [token, email, role, name])

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}

export function useAuth() {
  const ctx = useContext(Ctx)
  if (!ctx) throw new Error('AuthProvider missing')
  return ctx
}
