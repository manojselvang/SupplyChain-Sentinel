import { useState, useEffect, useCallback } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import PageHeader from '../components/PageHeader'
import ConfirmDialog from '../components/ConfirmDialog'
import ApplicationFormModal from './ApplicationFormModal'
import { getApplication, deleteApplication } from '../api/applications'
import { extractApiError } from '../api/client'
import { useAuth } from '../context/AuthContext'
import type { Application } from '../types'

// ── Detail field component ─────────────────────────────────

function Field({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-0.5">
      <span className="text-xs text-sentinel-400 uppercase tracking-wide">{label}</span>
      <span className="text-sm text-sentinel-200">{value || '—'}</span>
    </div>
  )
}

function Badge({ value, type }: { value: string; type: 'criticality' | 'status' }) {
  const cls = type === 'criticality'
    ? value === 'CRITICAL' ? 'badge-critical'
      : value === 'HIGH' ? 'badge-high'
      : value === 'MEDIUM' ? 'badge-medium'
      : 'badge-low'
    : value === 'ACTIVE' ? 'bg-green-500/15 text-green-400 border border-green-500/30'
      : value === 'DEPRECATED' ? 'bg-amber-500/15 text-amber-400 border border-amber-500/30'
      : value === 'PLANNING' ? 'bg-blue-500/15 text-blue-400 border border-blue-500/30'
      : 'bg-sentinel-700/40 text-sentinel-400 border border-sentinel-600/50'
  return <span className={`badge ${cls}`}>{value}</span>
}

// ── Main component ──────────────────────────────────────────

export default function ApplicationDetail() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { hasRole, hasAnyRole } = useAuth()

  const canEdit = hasAnyRole('ADMIN', 'SECURITY_ENGINEER')
  const canDelete = hasRole('ADMIN')

  const [app, setApp] = useState<Application | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [formOpen, setFormOpen] = useState(false)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [deleting, setDeleting] = useState(false)

  const fetchApp = useCallback(async () => {
    if (!id) return
    setLoading(true)
    setError(null)
    try {
      const result = await getApplication(id)
      setApp(result)
    } catch (err) {
      setError(extractApiError(err as Parameters<typeof extractApiError>[0]).message)
    } finally {
      setLoading(false)
    }
  }, [id])

  useEffect(() => {
    fetchApp()
  }, [fetchApp])

  const handleDelete = async () => {
    if (!app) return
    setDeleting(true)
    try {
      await deleteApplication(app.id)
      navigate('/applications')
    } catch (err) {
      setError(extractApiError(err as Parameters<typeof extractApiError>[0]).message)
      setDeleteOpen(false)
    } finally {
      setDeleting(false)
    }
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center py-16">
        <div className="w-8 h-8 border-2 border-accent-500 border-t-transparent rounded-full animate-spin" />
      </div>
    )
  }

  if (error || !app) {
    return (
      <div>
        <PageHeader title="Application Details" />
        <div className="card p-6 text-center">
          <p className="text-sm text-red-400 mb-3">{error || 'Application not found'}</p>
          <button onClick={() => navigate('/applications')} className="btn-secondary text-sm">
            Back to Applications
          </button>
        </div>
      </div>
    )
  }

  return (
    <div>
      <PageHeader
        title={app.name}
        subtitle={`Application key: ${app.applicationKey}`}
        actions={
          <div className="flex items-center gap-2">
            <button onClick={() => navigate('/applications')} className="btn-secondary text-sm">
              ← Back
            </button>
            {canEdit && (
              <button onClick={() => setFormOpen(true)} className="btn-secondary text-sm">
                Edit
              </button>
            )}
            {canDelete && (
              <button
                onClick={() => setDeleteOpen(true)}
                className="text-sm px-4 py-2 rounded-lg font-medium text-white bg-red-600 hover:bg-red-700 transition-colors"
              >
                Delete
              </button>
            )}
          </div>
        }
      />

      {error && (
        <div className="card p-4 mb-4 bg-red-500/10 border-red-500/30">
          <p className="text-sm text-red-400">{error}</p>
        </div>
      )}

      {/* Key information */}
      <div className="card p-5 mb-4">
        <h3 className="text-sm font-medium text-sentinel-200 mb-4">Overview</h3>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          <Field label="Name" value={app.name} />
          <Field label="Application Key" value={<span className="font-mono text-xs">{app.applicationKey}</span>} />
          <Field label="Description" value={app.description} />
          <Field label="Business Unit" value={app.businessUnit} />
          <Field label="Business Owner" value={app.businessOwner} />
          <Field label="Technical Owner" value={app.technicalOwner} />
        </div>
      </div>

      {/* Security info */}
      <div className="card p-5 mb-4">
        <h3 className="text-sm font-medium text-sentinel-200 mb-4">Security &amp; Deployment</h3>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          <div className="flex flex-col gap-0.5">
            <span className="text-xs text-sentinel-400 uppercase tracking-wide">Criticality</span>
            <Badge value={app.criticality} type="criticality" />
          </div>
          <div className="flex flex-col gap-0.5">
            <span className="text-xs text-sentinel-400 uppercase tracking-wide">Lifecycle Status</span>
            <Badge value={app.lifecycleStatus} type="status" />
          </div>
          <Field label="Data Classification" value={app.dataClassification} />
          <Field label="Hosting Environment" value={app.hostingEnvironment.replace('_', ' ')} />
          <Field label="Internet Facing" value={app.internetFacing ? 'Yes' : 'No'} />
          <Field label="Primary Language" value={app.primaryLanguage} />
        </div>
      </div>

      {/* Repository and timestamps */}
      <div className="card p-5 mb-4">
        <h3 className="text-sm font-medium text-sentinel-200 mb-4">Links &amp; Metadata</h3>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <Field
            label="Repository URL"
            value={app.repositoryUrl ? (
              <a href={app.repositoryUrl} target="_blank" rel="noopener noreferrer" className="text-accent-400 hover:underline">
                {app.repositoryUrl}
              </a>
            ) : null}
          />
          <Field label="Created At" value={app.createdAt ? new Date(app.createdAt).toLocaleString() : '—'} />
          <Field label="Updated At" value={app.updatedAt ? new Date(app.updatedAt).toLocaleString() : '—'} />
        </div>
      </div>

      {/* Edit modal */}
      <ApplicationFormModal
        open={formOpen}
        onClose={() => setFormOpen(false)}
        onSaved={fetchApp}
        app={app}
      />

      {/* Delete confirmation */}
      <ConfirmDialog
        open={deleteOpen}
        title="Delete Application"
        message={`Are you sure you want to delete "${app.name}" (${app.applicationKey})? This action cannot be undone.`}
        confirmLabel="Delete"
        destructive
        loading={deleting}
        onConfirm={handleDelete}
        onCancel={() => setDeleteOpen(false)}
      />
    </div>
  )
}
