import { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import PageHeader from '../components/PageHeader'
import Pagination from '../components/Pagination'
import { listAssessments, createAssessment } from '../api/releaseAssessments'
import { listApplications } from '../api/applications'
import { extractApiError } from '../api/client'
import type { ReleaseAssessment, Page, Application } from '../types'
import { ASSESSMENT_DECISIONS } from '../types'

// ── Decision badge ───────────────────────────────────────────

function DecisionBadge({ v }: { v: string }) {
  const c = v === 'PASS'
    ? 'bg-green-500/15 text-green-400 border border-green-500/30'
    : v === 'WARN'
    ? 'bg-amber-500/15 text-amber-400 border border-amber-500/30'
    : 'bg-red-500/15 text-red-400 border border-red-500/30'
  return <span className={`badge ${c} font-semibold`}>{v}</span>
}

function StatusBadge({ v }: { v: string }) {
  const c = v === 'READY_FOR_DEPLOYMENT'
    ? 'bg-green-500/15 text-green-400 border border-green-500/30'
    : v === 'REJECTED'
    ? 'bg-red-500/15 text-red-400 border border-red-500/30'
    : v === 'BLOCKED'
    ? 'bg-red-500/15 text-red-400 border border-red-500/30'
    : 'bg-sentinel-700/40 text-sentinel-400 border border-sentinel-600/50'
  return <span className={`badge ${c}`}>{v.replace('_', ' ')}</span>
}

// ── Create dialog ────────────────────────────────────────────

function CreateDialog({ open, onClose, onCreated }: {
  open: boolean; onClose: () => void; onCreated: () => void
}) {
  const [apps, setApps] = useState<Application[]>([])
  const [selectedApp, setSelectedApp] = useState('')
  const [pipelineRunId, setPipelineRunId] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!open) return
    listApplications({ size: 100, sort: 'name,asc' }).then(p => setApps(p.content)).catch(() => {})
  }, [open])

  const handleCreate = async () => {
    if (!selectedApp || !pipelineRunId.trim()) { setError('Both fields are required'); return }
    setLoading(true); setError(null)
    try {
      await createAssessment(selectedApp, pipelineRunId.trim())
      onCreated(); onClose()
    } catch (err) {
      setError(extractApiError(err as Parameters<typeof extractApiError>[0]).message)
    } finally { setLoading(false) }
  }

  if (!open) return null
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="fixed inset-0 bg-black/60" onClick={onClose} />
      <div className="relative w-full max-w-md bg-sentinel-900 border border-sentinel-800 rounded-lg shadow-xl p-6">
        <h2 className="text-lg font-semibold text-sentinel-100 mb-4">Create Release Assessment</h2>
        <p className="text-xs text-sentinel-400 mb-4">
          The risk engine will evaluate available evidence (security scan, SBOM, Cosign verification)
          and produce a deterministic PASS/WARN/BLOCK decision.
        </p>
        <div className="space-y-3">
          <div>
            <label className="block text-sm text-sentinel-300 mb-1.5">Application</label>
            <select value={selectedApp} onChange={e => setSelectedApp(e.target.value)} className="input-field w-full">
              <option value="">Select application...</option>
              {apps.map(a => <option key={a.id} value={a.id}>{a.name} ({a.applicationKey})</option>)}
            </select>
          </div>
          <div>
            <label className="block text-sm text-sentinel-300 mb-1.5">Pipeline Run ID (UUID)</label>
            <input type="text" value={pipelineRunId} onChange={e => setPipelineRunId(e.target.value)}
              className="input-field w-full" placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx" />
          </div>
          {error && <p className="text-sm text-red-400 bg-red-500/10 px-3 py-2 rounded-lg">{error}</p>}
          <div className="flex justify-end gap-2 pt-2">
            <button onClick={onClose} disabled={loading} className="btn-secondary text-sm">Cancel</button>
            <button onClick={handleCreate} disabled={loading} className="btn-primary text-sm">
              {loading ? 'Evaluating...' : 'Create Assessment'}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}

// ── Main page ────────────────────────────────────────────────

export default function Releases() {
  const navigate = useNavigate()
  const [data, setData] = useState<Page<ReleaseAssessment> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [decisionFilter, setDecisionFilter] = useState('')
  const [page, setPage] = useState(0)
  const [createOpen, setCreateOpen] = useState(false)

  const fetchData = useCallback(async () => {
    setLoading(true); setError(null)
    try {
      const result = await listAssessments({ decision: decisionFilter || undefined, page, size: 20, sort: 'assessedAt,desc' })
      setData(result)
    } catch (err) {
      setError(extractApiError(err as Parameters<typeof extractApiError>[0]).message)
    } finally { setLoading(false) }
  }, [decisionFilter, page])

  useEffect(() => { fetchData() }, [fetchData])

  return (
    <div>
      <PageHeader
        title="Release Management"
        subtitle="Deterministic release risk assessment — SCS-RELEASE-POLICY-V1"
        actions={
          <div className="flex items-center gap-2">
            <button onClick={fetchData} className="btn-secondary text-sm">
              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M16.023 9.348h4.992v-.001M2.985 19.644v-4.992m0 0h4.992m-4.993 0l3.181 3.183a8.25 8.25 0 0011.667 0l3.181-3.183m-4.991-2.698v-4.992m0 0h-4.992m4.992 0l-3.181-3.183a8.25 8.25 0 00-11.667 0l-3.18 3.183" />
              </svg>
            </button>
            <button onClick={() => setCreateOpen(true)} className="btn-primary text-sm">+ New Assessment</button>
          </div>
        }
      />

      {/* Filters */}
      <div className="flex gap-3 mb-4">
        <select value={decisionFilter} onChange={e => { setDecisionFilter(e.target.value); setPage(0) }} className="input-field sm:w-40">
          <option value="">All Decisions</option>
          {ASSESSMENT_DECISIONS.map(d => <option key={d} value={d}>{d}</option>)}
        </select>
      </div>

      {error && <div className="card p-4 mb-4 bg-red-500/10 border-red-500/30"><p className="text-sm text-red-400">{error}</p></div>}

      <div className="card overflow-hidden">
        {loading ? (
          <div className="flex items-center justify-center py-16">
            <div className="w-8 h-8 border-2 border-accent-500 border-t-transparent rounded-full animate-spin" />
          </div>
        ) : !data || data.content.length === 0 ? (
          <div className="px-4 py-16 text-center text-sentinel-500">
            No release assessments yet. Click "+ New Assessment" to evaluate a pipeline run.
          </div>
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-sentinel-850 border-b border-sentinel-800">
                  <tr className="text-left text-sentinel-400">
                    <th className="px-4 py-3 font-medium">Application</th>
                    <th className="px-4 py-3 font-medium">Risk Score</th>
                    <th className="px-4 py-3 font-medium">Decision</th>
                    <th className="px-4 py-3 font-medium">Status</th>
                    <th className="px-4 py-3 font-medium">Policy</th>
                    <th className="px-4 py-3 font-medium">Requested By</th>
                    <th className="px-4 py-3 font-medium">Assessed At</th>
                    <th className="px-4 py-3 font-medium text-right">Details</th>
                  </tr>
                </thead>
                <tbody>
                  {data.content.map(a => (
                    <tr key={a.id} className="border-b border-sentinel-800/50 hover:bg-sentinel-850/50">
                      <td className="px-4 py-3">
                        <button onClick={() => navigate(`/applications/${a.applicationId}`)}
                          className="text-sentinel-100 hover:text-accent-400 font-medium transition-colors">
                          {a.applicationName}
                        </button>
                      </td>
                      <td className="px-4 py-3">
                        <span className={`text-lg font-bold ${a.riskScore >= 50 ? 'text-red-400' : a.riskScore >= 25 ? 'text-amber-400' : 'text-green-400'}`}>
                          {a.riskScore}
                        </span>
                        <span className="text-xs text-sentinel-500">/100</span>
                      </td>
                      <td className="px-4 py-3"><DecisionBadge v={a.decision} /></td>
                      <td className="px-4 py-3"><StatusBadge v={a.assessmentStatus} /></td>
                      <td className="px-4 py-3 text-sentinel-400 text-xs">{a.policyVersion}</td>
                      <td className="px-4 py-3 text-sentinel-400 text-xs">{a.requestedBy || '—'}</td>
                      <td className="px-4 py-3 text-sentinel-400 text-xs">{new Date(a.assessedAt).toLocaleString()}</td>
                      <td className="px-4 py-3 text-right">
                        <button onClick={() => navigate(`/releases/${a.id}`)}
                          className="text-xs btn-secondary py-1 px-2">
                          View
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination page={data.number} size={data.size} totalElements={data.totalElements} totalPages={data.totalPages} onPageChange={setPage} />
          </>
        )}
      </div>

      <CreateDialog open={createOpen} onClose={() => setCreateOpen(false)} onCreated={fetchData} />
    </div>
  )
}
