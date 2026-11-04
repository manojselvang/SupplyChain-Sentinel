import { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import PageHeader from '../components/PageHeader'
import Pagination from '../components/Pagination'
import ConfirmDialog from '../components/ConfirmDialog'
import ApplicationFormModal from './ApplicationFormModal'
import { listApplications, deleteApplication } from '../api/applications'
import { extractApiError } from '../api/client'
import { useAuth } from '../context/AuthContext'
import {
  CRITICALITY_LEVELS,
  LIFECYCLE_STATUSES,
  type Application,
  type Page,
} from '../types'

// ── Badge helpers ───────────────────────────────────────────

function CriticalityBadge({ value }: { value: string }) {
  const cls = value === 'CRITICAL' ? 'badge-critical'
    : value === 'HIGH' ? 'badge-high'
    : value === 'MEDIUM' ? 'badge-medium'
    : 'badge-low'
  return <span className={`badge ${cls}`}>{value}</span>
}

function StatusBadge({ value }: { value: string }) {
  const cls = value === 'ACTIVE' ? 'bg-green-500/15 text-green-400 border border-green-500/30'
    : value === 'DEPRECATED' ? 'bg-amber-500/15 text-amber-400 border border-amber-500/30'
    : value === 'PLANNING' ? 'bg-blue-500/15 text-blue-400 border border-blue-500/30'
    : 'bg-sentinel-700/40 text-sentinel-400 border border-sentinel-600/50'
  return <span className={`badge ${cls}`}>{value}</span>
}

// ── Sortable header ─────────────────────────────────────────

interface SortConfig {
  field: string
  dir: 'asc' | 'desc'
}

function SortHeader({
  label, field, sort, onSort,
}: {
  label: string
  field: string
  sort: SortConfig | null
  onSort: (field: string) => void
}) {
  const active = sort?.field === field
  return (
    <th
      className="px-4 py-3 font-medium cursor-pointer select-none hover:text-sentinel-200 transition-colors"
      onClick={() => onSort(field)}
    >
      <span className="flex items-center gap-1">
        {label}
        {active && (
          <svg className="w-3 h-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
            {sort!.dir === 'asc'
              ? <path strokeLinecap="round" strokeLinejoin="round" d="M4.5 15.75l7.5-7.5 7.5 7.5" />
              : <path strokeLinecap="round" strokeLinejoin="round" d="M19.5 8.25l-7.5 7.5-7.5-7.5" />}
          </svg>
        )}
      </span>
    </th>
  )
}

// ── Main component ──────────────────────────────────────────

export default function Applications() {
  const navigate = useNavigate()
  const { hasRole, hasAnyRole } = useAuth()

  const canCreate = hasAnyRole('ADMIN', 'SECURITY_ENGINEER')
  const canEdit = hasAnyRole('ADMIN', 'SECURITY_ENGINEER')
  const canDelete = hasRole('ADMIN')

  // Data state
  const [data, setData] = useState<Page<Application> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // Filter state
  const [searchInput, setSearchInput] = useState('')
  const [search, setSearch] = useState('')
  const [criticalityFilter, setCriticalityFilter] = useState('')
  const [lifecycleFilter, setLifecycleFilter] = useState('')

  // Pagination state
  const [page, setPage] = useState(0)
  const pageSize = 20

  // Sort state
  const [sort, setSort] = useState<SortConfig | null>({ field: 'name', dir: 'asc' })

  // Modal state
  const [formOpen, setFormOpen] = useState(false)
  const [editApp, setEditApp] = useState<Application | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<Application | null>(null)
  const [deleting, setDeleting] = useState(false)

  // Debounce search input
  useEffect(() => {
    const timer = setTimeout(() => {
      setSearch(searchInput)
      setPage(0)
    }, 300)
    return () => clearTimeout(timer)
  }, [searchInput])

  // Fetch data
  const fetchData = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const result = await listApplications({
        search: search || undefined,
        criticality: criticalityFilter || undefined,
        lifecycleStatus: lifecycleFilter || undefined,
        page,
        size: pageSize,
        sort: sort ? `${sort.field},${sort.dir}` : undefined,
      })
      setData(result)
    } catch (err) {
      setError(extractApiError(err as Parameters<typeof extractApiError>[0]).message)
    } finally {
      setLoading(false)
    }
  }, [search, criticalityFilter, lifecycleFilter, page, sort])

  useEffect(() => {
    fetchData()
  }, [fetchData])

  // Handlers

  const handleSort = (field: string) => {
    setSort(prev => {
      if (prev?.field === field) {
        return { field, dir: prev.dir === 'asc' ? 'desc' : 'asc' }
      }
      return { field, dir: 'asc' }
    })
    setPage(0)
  }

  const handleCreate = () => {
    setEditApp(null)
    setFormOpen(true)
  }

  const handleEdit = (app: Application) => {
    setEditApp(app)
    setFormOpen(true)
  }

  const handleDelete = async () => {
    if (!deleteTarget) return
    setDeleting(true)
    try {
      await deleteApplication(deleteTarget.id)
      setDeleteTarget(null)
      fetchData()
    } catch (err) {
      setError(extractApiError(err as Parameters<typeof extractApiError>[0]).message)
      setDeleteTarget(null)
    } finally {
      setDeleting(false)
    }
  }

  const handleFilterChange = (setter: (v: string) => void) => (e: React.ChangeEvent<HTMLSelectElement>) => {
    setter(e.target.value)
    setPage(0)
  }

  return (
    <div>
      <PageHeader
        title="Applications"
        subtitle="Application inventory — register, manage and track security posture"
        actions={
          <div className="flex items-center gap-2">
            <button onClick={fetchData} className="btn-secondary text-sm" title="Refresh">
              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M16.023 9.348h4.992v-.001M2.985 19.644v-4.992m0 0h4.992m-4.993 0l3.181 3.183a8.25 8.25 0 0011.667 0l3.181-3.183m-4.991-2.698v-4.992m0 0h-4.992m4.992 0l-3.181-3.183a8.25 8.25 0 00-11.667 0l-3.18 3.183" />
              </svg>
              <span className="hidden sm:inline">Refresh</span>
            </button>
            {canCreate && (
              <button onClick={handleCreate} className="btn-primary text-sm">
                + Register Application
              </button>
            )}
          </div>
        }
      />

      {/* Filters bar */}
      <div className="flex flex-col sm:flex-row gap-3 mb-4">
        <input
          type="text"
          value={searchInput}
          onChange={(e) => setSearchInput(e.target.value)}
          className="input-field flex-1"
          placeholder="Search by name or application key..."
        />
        <select
          value={criticalityFilter}
          onChange={handleFilterChange(setCriticalityFilter)}
          className="input-field sm:w-40"
        >
          <option value="">All Criticalities</option>
          {CRITICALITY_LEVELS.map((c) => <option key={c} value={c}>{c}</option>)}
        </select>
        <select
          value={lifecycleFilter}
          onChange={handleFilterChange(setLifecycleFilter)}
          className="input-field sm:w-40"
        >
          <option value="">All Statuses</option>
          {LIFECYCLE_STATUSES.map((s) => <option key={s} value={s}>{s}</option>)}
        </select>
      </div>

      {/* Error state */}
      {error && (
        <div className="card p-4 mb-4 bg-red-500/10 border-red-500/30">
          <p className="text-sm text-red-400">{error}</p>
        </div>
      )}

      {/* Table */}
      <div className="card overflow-hidden">
        {loading ? (
          <div className="flex items-center justify-center py-16">
            <div className="w-8 h-8 border-2 border-accent-500 border-t-transparent rounded-full animate-spin" />
          </div>
        ) : !data || data.content.length === 0 ? (
          <div className="px-4 py-16 text-center text-sentinel-500">
            {search || criticalityFilter || lifecycleFilter
              ? 'No applications match your filters.'
              : 'No applications registered yet. Click "Register Application" to add one.'}
          </div>
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-sentinel-850 border-b border-sentinel-800">
                  <tr className="text-left text-sentinel-400">
                    <SortHeader label="Name" field="name" sort={sort} onSort={handleSort} />
                    <SortHeader label="App Key" field="applicationKey" sort={sort} onSort={handleSort} />
                    <th className="px-4 py-3 font-medium">Business Unit</th>
                    <SortHeader label="Criticality" field="criticality" sort={sort} onSort={handleSort} />
                    <SortHeader label="Lifecycle" field="lifecycleStatus" sort={sort} onSort={handleSort} />
                    <th className="px-4 py-3 font-medium">Hosting</th>
                    <th className="px-4 py-3 font-medium">Internet</th>
                    <th className="px-4 py-3 font-medium text-right">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {data.content.map((app) => (
                    <tr key={app.id} className="border-b border-sentinel-800/50 hover:bg-sentinel-850/50 transition-colors">
                      <td className="px-4 py-3">
                        <button
                          onClick={() => navigate(`/applications/${app.id}`)}
                          className="text-sentinel-100 hover:text-accent-400 font-medium transition-colors"
                        >
                          {app.name}
                        </button>
                      </td>
                      <td className="px-4 py-3 text-sentinel-400 font-mono text-xs">{app.applicationKey}</td>
                      <td className="px-4 py-3 text-sentinel-400">{app.businessUnit || '—'}</td>
                      <td className="px-4 py-3"><CriticalityBadge value={app.criticality} /></td>
                      <td className="px-4 py-3"><StatusBadge value={app.lifecycleStatus} /></td>
                      <td className="px-4 py-3 text-sentinel-400 text-xs">{app.hostingEnvironment.replace('_', ' ')}</td>
                      <td className="px-4 py-3">
                        {app.internetFacing
                          ? <span className="text-amber-400 text-xs">Yes</span>
                          : <span className="text-sentinel-500 text-xs">No</span>}
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex items-center justify-end gap-1">
                          <button
                            onClick={() => navigate(`/applications/${app.id}`)}
                            className="p-1.5 text-sentinel-400 hover:text-sentinel-200 hover:bg-sentinel-800 rounded-lg transition-colors"
                            title="View"
                          >
                            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.8}>
                              <path strokeLinecap="round" strokeLinejoin="round" d="M2.036 12.322a1.012 1.012 0 010-.639C3.423 7.51 7.36 4.5 12 4.5c4.638 0 8.573 3.007 9.963 7.178.07.207.07.431 0 .639C20.577 16.49 16.64 19.5 12 19.5c-4.638 0-8.573-3.007-9.963-7.178z" />
                              <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                            </svg>
                          </button>
                          {canEdit && (
                            <button
                              onClick={() => handleEdit(app)}
                              className="p-1.5 text-sentinel-400 hover:text-accent-400 hover:bg-sentinel-800 rounded-lg transition-colors"
                              title="Edit"
                            >
                              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.8}>
                                <path strokeLinecap="round" strokeLinejoin="round" d="M16.862 4.487l1.687-1.688a1.875 1.875 0 112.652 2.652L10.582 16.07a4.5 4.5 0 01-1.897 1.13L6.18 18.06a.75.75 0 01-.728-.031.75.75 0 01-.03-.728l1.36-3.341a4.5 4.5 0 011.13-1.897l9.95-9.95z" />
                              </svg>
                            </button>
                          )}
                          {canDelete && (
                            <button
                              onClick={() => setDeleteTarget(app)}
                              className="p-1.5 text-sentinel-400 hover:text-red-400 hover:bg-sentinel-800 rounded-lg transition-colors"
                              title="Delete"
                            >
                              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.8}>
                                <path strokeLinecap="round" strokeLinejoin="round" d="M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 01-2.244 2.077H8.084a2.25 2.25 0 01-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 00-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 013.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 00-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 00-7.5 0" />
                              </svg>
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <Pagination
              page={data.number}
              size={data.size}
              totalElements={data.totalElements}
              totalPages={data.totalPages}
              onPageChange={setPage}
            />
          </>
        )}
      </div>

      {/* Create / Edit modal */}
      <ApplicationFormModal
        open={formOpen}
        onClose={() => setFormOpen(false)}
        onSaved={fetchData}
        app={editApp}
      />

      {/* Delete confirmation */}
      <ConfirmDialog
        open={!!deleteTarget}
        title="Delete Application"
        message={`Are you sure you want to delete "${deleteTarget?.name}" (${deleteTarget?.applicationKey})? This action cannot be undone.`}
        confirmLabel="Delete"
        destructive
        loading={deleting}
        onConfirm={handleDelete}
        onCancel={() => setDeleteTarget(null)}
      />
    </div>
  )
}
