import type { ReactNode } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth/AuthContext'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'
import { DesignsPage } from './pages/DesignsPage'
import { DesignWizardPage } from './pages/DesignWizardPage'
import { JobsPage } from './pages/JobsPage'
import { JobPage } from './pages/JobPage'
import { AdminPage } from './pages/admin/AdminPage'

function Private({ children }: { children: ReactNode }) {
  const { token } = useAuth()
  if (!token) return <Navigate to="/login" replace />
  return children
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/designs" element={<Private><DesignsPage /></Private>} />
      <Route path="/designs/:id" element={<Private><DesignWizardPage /></Private>} />
      <Route path="/jobs" element={<Private><JobsPage /></Private>} />
      <Route path="/jobs/:id" element={<Private><JobPage /></Private>} />
      <Route path="/admin" element={<Private><AdminPage /></Private>} />
      <Route path="*" element={<Navigate to="/designs" replace />} />
    </Routes>
  )
}
