import { useState, useEffect, useCallback } from 'react'
import PageHeader from '../components/PageHeader'
import Pagination from '../components/Pagination'
import { listPipelineRuns } from '../api/pipelineRuns'
import { extractApiError } from '../api/client'
import { PIPELINE_PROVIDERS, PIPELINE_STATUSES, type PipelineRun, type Page } from '../types'

function StatusBadge({ value }: { value: string }) {
  const cls = value === 'SUCCESS' ? 'bg-green-500/15 text-green-400 border border-green-500/30'
    : value === 'FAILED' ? 'bg-red-500/15 text-red-400 border border-red-500/30'
    : value === 'RUNNING' ? 'bg-blue-500/15 text-blue-400 border border-blue-500/30'
    : value === 'QUEUED' ? 'bg-sentinel-700/40 text-sentinel-400 border border-sentinel-600/50'
    : 'bg-accent-500/15 text-accent-400 border border-accent-500/30'
  return <span className={`badge ${cls}`}>{value}</span>
}

function formatDuration(startedAt: string | null, completedAt: string | null): string {
  if (!startedAt || !completedAt) return '—'
  const start = new Date(startedAt).getTime()
  const end = new Date(completedAt).getTime()
  const seconds = Math.round((end - start) / 1000)
  if (seconds < 60) return `${seconds}s`
  const minutes = Math.floor(seconds / 60)
  const remainingSeconds = seconds % 60
  return `${minutes}m ${remainingSeconds}s`
}

export default function Pipelines() {
  const [data, setData] = useState<Page<PipelineRun> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [providerFilter, setProviderFilter] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [page, setPage] = useState(0)
  const pageSize = 20

  const fetchData = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const result = await listPipelineRuns({
        provider: providerFilter || undefined,
        status: statusFilter || undefined,
        page,
        size: pageSize,
        sort: 'startedAt,desc',
      })
      setData(result)
    } catch (err) {
      setError(extractApiError(err as Parameters<typeof extractApiError>[0]).message)
    } finally {
      setLoading(false)
    }
  }, [providerFilter, statusFilter, page])

  useEffect(() => {
    fetchData()
  }, [fetchData])

  return (
    <div>
      <PageHeader
        title="CI/CD Pipelines"
        subtitle="Pipeline executions and build metadata"
        actions={
          <button onClick={fetchData} className="btn-secondary text-sm">
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M16.023 9.348h4.992v-.001M2.985 19.644v-4.992m0 0h4.992m-4.993 0l3.181 3.183a8.25 8.25 0 0011.667 0l3.181-3.183m-4.991-2.698v-4.992m0 0h-4.992m4.992 0l-3.181-3.183a8.25 8.25 0 00-11.667 0l-3.18 3.183" />
            </svg>
            <span className="hidden sm:inline">Refresh</span>
          </button>
        }
      />

      {/* Filters */}
      <div className="flex flex-col sm:flex-row gap-3 mb-4">
        <select
          value={providerFilter}
          onChange={(e) => { setProviderFilter(e.target.value); setPage(0) }}
          className="input-field sm:w-44"
        >
          <option value="">All Providers</option>
          {PIPELINE_PROVIDERS.map((p) => <option key={p} value={p}>{p.replace('_', ' ')}</option>)}
        </select>
        <select
          value={statusFilter}
          onChange={(e) => { setStatusFilter(e.target.value); setPage(0) }}
          className="input-field sm:w-44"
        >
          <option value="">All Statuses</option>
          {PIPELINE_STATUSES.map((s) => <option key={s} value={s}>{s}</option>)}
        </select>
      </div>

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
            {providerFilter || statusFilter
              ? 'No pipeline runs match your filters.'
              : 'No pipeline runs yet. Configure your CI/CD pipeline to submit build metadata.'}
          </div>
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-sentinel-850 border-b border-sentinel-800">
                  <tr className="text-left text-sentinel-400">
                    <th className="px-4 py-3 font-medium">Application</th>
                    <th className="px-4 py-3 font-medium">Provider</th>
                    <th className="px-4 py-3 font-medium">Workflow</th>
                    <th className="px-4 py-3 font-medium">Branch</th>
                    <th className="px-4 py-3 font-medium">Commit</th>
                    <th className="px-4 py-3 font-medium">Status</th>
                    <th className="px-4 py-3 font-medium">Started</th>
                    <th className="px-4 py-3 font-medium">Duration</th>
                    <th className="px-4 py-3 font-medium">Artifact Digest</th>
                  </tr>
                </thead>
                <tbody>
                  {data.content.map((run) => (
                    <tr key={run.id} className="border-b border-sentinel-800/50 hover:bg-sentinel-850/50 transition-colors">
                      <td className="px-4 py-3 text-sentinel-100 font-medium">{run.applicationName}</td>
                      <td className="px-4 py-3 text-sentinel-400 text-xs">{run.provider.replace('_', ' ')}</td>
                      <td className="px-4 py-3 text-sentinel-300">{run.workflowName || '—'}</td>
                      <td className="px-4 py-3 text-sentinel-400 font-mono text-xs">{run.branchName || '—'}</td>
                      <td className="px-4 py-3 text-sentinel-400 font-mono text-xs">{run.commitSha ? run.commitSha.substring(0, 8) : '—'}</td>
                      <td className="px-4 py-3"><StatusBadge value={run.status} /></td>
                      <td className="px-4 py-3 text-sentinel-400 text-xs">{run.startedAt ? new Date(run.startedAt).toLocaleString() : '—'}</td>
                      <td className="px-4 py-3 text-sentinel-400 text-xs">{formatDuration(run.startedAt, run.completedAt)}</td>
                      <td className="px-4 py-3 text-sentinel-400 font-mono text-xs">{run.artifactDigest ? run.artifactDigest.substring(0, 12) + '…' : '—'}</td>
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
    </div>
  )
}
