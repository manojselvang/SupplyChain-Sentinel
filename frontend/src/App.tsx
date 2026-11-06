import { Routes, Route, Navigate } from 'react-router-dom'
import MainLayout from './layouts/MainLayout'
import ProtectedRoute from './routes/ProtectedRoute'
import Login from './pages/Login'
import Dashboard from './pages/Dashboard'
import Applications from './pages/Applications'
import ApplicationDetail from './pages/ApplicationDetail'
import Pipelines from './pages/Pipelines'
import Findings from './pages/Findings'
import Sbom from './pages/Sbom'
import ArtifactVerifications from './pages/ArtifactVerifications'
import Releases from './pages/Releases'
import Policies from './pages/Policies'
import Deployments from './pages/Deployments'
import AuditLogs from './pages/AuditLogs'
import Administration from './pages/Administration'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route
        element={
          <ProtectedRoute>
            <MainLayout />
          </ProtectedRoute>
        }
      >
        <Route index element={<Dashboard />} />
        <Route path="applications" element={<Applications />} />
        <Route path="applications/:id" element={<ApplicationDetail />} />
        <Route path="pipelines" element={<Pipelines />} />
        <Route path="findings" element={<Findings />} />
        <Route path="sbom" element={<Sbom />} />
        <Route path="artifact-verifications" element={<ArtifactVerifications />} />
        <Route path="releases" element={<Releases />} />
        <Route path="policies" element={<Policies />} />
        <Route path="deployments" element={<Deployments />} />
        <Route path="audit-logs" element={<AuditLogs />} />
        <Route path="admin" element={<Administration />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
