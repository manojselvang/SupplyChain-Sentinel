import { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import PageHeader from '../components/PageHeader'
import Pagination from '../components/Pagination'
import { listDeployments } from '../api/deployments'
import { extractApiError } from '../api/client'
import { SYNC_STATUSES, HEALTH_STATUSES, type DeploymentRecord, type Page } from '../types'

function SyncBadge({ v }: { v: string }) {
  const c = v === 'SYNCED' ? 'bg-green-500/15 text-green-400 border border-green-500/30'
    : v === 'OUT_OF_SYNC' ? 'bg-amber-500/15 text-amber-400 border border-amber-500/30'
    : v === 'ERROR' ? 'bg-red-500/15 text-red-400 border border-red-500/30'
    : 'bg-sentinel-700/40 text-sentinel-400 border border-sentinel-600/50'
  return <span className={`badge ${c}`}>{v}</span>
}

function HealthBadge({ v }: { v: string }) {
  const c = v === 'HEALTHY' ? 'bg-green-500/15 text-green-400 border border-green-500/30'
    : v === 'DEGRADED' ? 'bg-red-500/15 text-red-400 border border-red-500/30'
    : v === 'PROGRESSING' ? 'bg-blue-500/15 text-blue-400 border border-blue-500/30'
    : v === 'MISSING' ? 'bg-amber-500/15 text-amber-400 border border-amber-500/30'
    : 'bg-sentinel-700/40 text-sentinel-400 border border-sentinel-600/50'
  return <span className={`badge ${c}`}>{v}</span>
}

export default function Deployments() {
  const navigate = useNavigate()
  const [data, setData] = useState<Page<DeploymentRecord> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [syncFilter, setSyncFilter] = useState('')
  const [healthFilter, setHealthFilter] = useState('')
  const [envFilter, setEnvFilter] = useState('')
  const [page, setPage] = useState(0)

  const fetchData = useCallback(async () => {
    setLoading(true); setError(null)
    try {
      const result = await listDeployments({
        syncStatus: syncFilter || undefined,
        healthStatus: healthFilter || undefined,
        environment: envFilter || undefined,
        page, size: 20, sort: 'deployedAt,desc',
      })
      setData(result)
    } catch (err) {
      setError(extractApiError(err as Parameters<typeof extractApiError>[0]).message)
    } finally { setLoading(false) }
  }, [syncFilter, healthFilter, envFilter, page])

  useEffect(() => { fetchData() }, [fetchData])

  const shortDigest = (d: string | null) => d ? d.substring(0, 12) + '…' : '—'

  return (
    <div>
      <PageHeader
        title="Deployments"
        subtitle="Argo CD deployment records — GitOps delivery tracking"
        actions={
          <button onClick={fetchData} className="btn-secondary text-sm">
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M16.023 9.348h4.992v-.001M2.985 19.644v-4.992m0 0h4.992m-4.993 0l3.181 3.183a8.25 8.25 0 0011.667 0l3.181-3.183m-4.991-2.698v-4.992m0 0h-4.992m4.992 0l-3.181-3.183a8.25 8.25 0 00-11.667 0l-3.18 3.183" />
            </svg>
            <span className="hidden sm:inline">Refresh</span>
          </button>
        }
      />

      <div className="flex flex-col sm:flex-row gap-3 mb-4">
        <select value={syncFilter} onChange={e => { setSyncFilter(e.target.value); setPage(0) }} className="input-field sm:w-40">
          <option value="">All Sync</option>
          {SYNC_STATUSES.map(s => <option key={s} value={s}>{s}</option>)}
        </select>
        <select value={healthFilter} onChange={e => { setHealthFilter(e.target.value); setPage(0) }} className="input-field sm:w-40">
          <option value="">All Health</option>
          {HEALTH_STATUSES.map(s => <option key={s} value={s}>{s}</option>)}
        </select>
        <input type="text" value={envFilter} onChange={e => { setEnvFilter(e.target.value); setPage(0) }}
          className="input-field sm:w-40" placeholder="Environment..." />
      </div>

      {error && <div className="card p-4 mb-4 bg-red-500/10 border-red-500/30"><p className="text-sm text-red-400">{error}</p></div>}

      <div className="card overflow-hidden">
        {loading ? (
          <div className="flex items-center justify-center py-16"><div className="w-8 h-8 border-2 border-accent-500 border-t-transparent rounded-full animate-spin" /></div>
        ) : !data || data.content.length === 0 ? (
          <div className="px-4 py-16 text-center text-sentinel-500">
            {syncFilter || healthFilter || envFilter
              ? 'No deployments match your filters.'
              : 'No deployment records yet. Configure Argo CD notifications to submit deployment events.'}
          </div>
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-sentinel-850 border-b border-sentinel-800">
                  <tr className="text-left text-sentinel-400">
                    <th className="px-4 py-3 font-medium">Application</th>
                    <th className="px-4 py-3 font-medium">Argo CD App</th>
                    <th className="px-4 py-3 font-medium">Environment</th>
                    <th className="px-4 py-3 font-medium">Cluster / NS</th>
                    <th className="px-4 py-3 font-medium">Image Digest</th>
                    <th className="px-4 py-3 font-medium">Git Revision</th>
                    <th className="px-4 py-3 font-medium">Sync</th>
                    <th className="px-4 py-3 font-medium">Health</th>
                    <th className="px-4 py-3 font-medium">Deployed</th>
                  </tr>
                </thead>
                <tbody>
                  {data.content.map(d => (
                    <tr key={d.id} className="border-b border-sentinel-800/50 hover:bg-sentinel-850/50 transition-colors">
                      <td className="px-4 py-3">
                        <button onClick={() => navigate(`/applications/${d.applicationId}`)}
                          className="text-sentinel-100 hover:text-accent-400 font-medium transition-colors">{d.applicationName}</button>
                      </td>
                      <td className="px-4 py-3 text-sentinel-300">{d.argocdApplication}</td>
                      <td className="px-4 py-3"><span className="badge badge-info">{d.environment}</span></td>
                      <td className="px-4 py-3 text-sentinel-400 text-xs">{d.cluster || '—'} / {d.namespace || '—'}</td>
                      <td className="px-4 py-3 text-sentinel-400 font-mono text-xs">
                        {shortDigest(d.imageDigest)}
                        {d.releaseAssessmentId && (
                          <span className={`ml-1 ${d.digestMatchesAssessment ? 'text-green-400' : 'text-red-400'}`}>
                            {d.digestMatchesAssessment ? '✓' : '✗'}
                          </span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-sentinel-400 font-mono text-xs">{d.gitRevision ? d.gitRevision.substring(0, 8) : '—'}</td>
                      <td className="px-4 py-3"><SyncBadge v={d.syncStatus} /></td>
                      <td className="px-4 py-3"><HealthBadge v={d.healthStatus} /></td>
                      <td className="px-4 py-3 text-sentinel-400 text-xs">{new Date(d.deployedAt).toLocaleString()}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination page={data.number} size={data.size} totalElements={data.totalElements} totalPages={data.totalPages} onPageChange={setPage} />
          </>
        )}
      </div>

      <div className="mt-4 p-3 bg-blue-500/10 border border-blue-500/30 rounded-lg">
        <p className="text-xs text-blue-300">
          ⓘ Digest correlation: <span className="text-green-400">✓</span> = deployed digest matches the assessed artifact digest. <span className="text-red-400">✗</span> = mismatch detected.
        </p>
      </div>
    </div>
  )
}
