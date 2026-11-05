import { useState, useEffect, useCallback } from 'react'
import PageHeader from '../components/PageHeader'
import Pagination from '../components/Pagination'
import { listSecurityFindings, updateFindingStatus } from '../api/securityFindings'
import { extractApiError } from '../api/client'
import { useAuth } from '../context/AuthContext'
import { FINDING_SEVERITIES, FINDING_STATUSES, type SecurityFinding, type Page } from '../types'

function SeverityBadge({ v }: { v: string }) {
  const c = v === 'CRITICAL' ? 'badge-critical'
    : v === 'HIGH' ? 'badge-high'
    : v === 'MEDIUM' ? 'badge-medium'
    : v === 'LOW' ? 'badge-low'
    : 'badge-info'
  return <span className={`badge ${c}`}>{v}</span>
}

function StatusBadge({ v }: { v: string }) {
  const c = v === 'OPEN' ? 'bg-red-500/15 text-red-400 border border-red-500/30'
    : v === 'ACKNOWLEDGED' ? 'bg-amber-500/15 text-amber-400 border border-amber-500/30'
    : v === 'REMEDIATED' ? 'bg-green-500/15 text-green-400 border border-green-500/30'
    : 'bg-blue-500/15 text-blue-400 border border-blue-500/30'
  return <span className={`badge ${c}`}>{v}</span>
}

export default function Findings() {
  const { hasAnyRole } = useAuth()
  const canUpdate = hasAnyRole('ADMIN', 'SECURITY_ENGINEER')

  const [data, setData] = useState<Page<SecurityFinding> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [searchInput, setSearchInput] = useState('')
  const [search, setSearch] = useState('')
  const [severityFilter, setSeverityFilter] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [scannerFilter, setScannerFilter] = useState('')
  const [page, setPage] = useState(0)
  const pageSize = 20
  const [updatingId, setUpdatingId] = useState<string | null>(null)

  useEffect(() => {
    const t = setTimeout(() => { setSearch(searchInput); setPage(0) }, 300)
    return () => clearTimeout(t)
  }, [searchInput])

  const fetchData = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const result = await listSecurityFindings({
        search: search || undefined,
        severity: severityFilter || undefined,
        status: statusFilter || undefined,
        scanner: scannerFilter || undefined,
        page, size: pageSize, sort: 'lastSeen,desc',
      })
      setData(result)
    } catch (err) {
      setError(extractApiError(err as Parameters<typeof extractApiError>[0]).message)
    } finally {
      setLoading(false)
    }
  }, [search, severityFilter, statusFilter, scannerFilter, page])

  useEffect(() => { fetchData() }, [fetchData])

  const handleStatusChange = async (id: string, status: string) => {
    setUpdatingId(id)
    try {
      await updateFindingStatus(id, status)
      fetchData()
    } catch (err) {
      setError(extractApiError(err as Parameters<typeof extractApiError>[0]).message)
    } finally {
      setUpdatingId(null)
    }
  }

  return (
    <div>
      <PageHeader
        title="Security Findings"
        subtitle="Normalized vulnerabilities and security issues across all scanners"
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
        <input
          type="text"
          value={searchInput}
          onChange={(e) => setSearchInput(e.target.value)}
          className="input-field flex-1"
          placeholder="Search by title or CVE..."
        />
        <select value={severityFilter} onChange={(e) => { setSeverityFilter(e.target.value); setPage(0) }} className="input-field sm:w-40">
          <option value="">All Severities</option>
          {FINDING_SEVERITIES.map((s) => <option key={s} value={s}>{s}</option>)}
        </select>
        <select value={statusFilter} onChange={(e) => { setStatusFilter(e.target.value); setPage(0) }} className="input-field sm:w-40">
          <option value="">All Statuses</option>
          {FINDING_STATUSES.map((s) => <option key={s} value={s}>{s}</option>)}
        </select>
        <input
          type="text"
          value={scannerFilter}
          onChange={(e) => { setScannerFilter(e.target.value); setPage(0) }}
          className="input-field sm:w-40"
          placeholder="Scanner..."
        />
      </div>

      {error && <div className="card p-4 mb-4 bg-red-500/10 border-red-500/30"><p className="text-sm text-red-400">{error}</p></div>}

      {/* Table */}
      <div className="card overflow-hidden">
        {loading ? (
          <div className="flex items-center justify-center py-16">
            <div className="w-8 h-8 border-2 border-accent-500 border-t-transparent rounded-full animate-spin" />
          </div>
        ) : !data || data.content.length === 0 ? (
          <div className="px-4 py-16 text-center text-sentinel-500">
            {search || severityFilter || statusFilter || scannerFilter
              ? 'No findings match your filters.'
              : 'No security findings yet. Ingest scanner reports to populate findings.'}
          </div>
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-sentinel-850 border-b border-sentinel-800">
                  <tr className="text-left text-sentinel-400">
                    <th className="px-4 py-3 font-medium">Application</th>
                    <th className="px-4 py-3 font-medium">Title</th>
                    <th className="px-4 py-3 font-medium">CVE / Rule</th>
                    <th className="px-4 py-3 font-medium">Severity</th>
                    <th className="px-4 py-3 font-medium">Scanner</th>
                    <th className="px-4 py-3 font-medium">Package</th>
                    <th className="px-4 py-3 font-medium">Installed</th>
                    <th className="px-4 py-3 font-medium">Fixed</th>
                    <th className="px-4 py-3 font-medium">Status</th>
                    <th className="px-4 py-3 font-medium">Last Seen</th>
                    {canUpdate && <th className="px-4 py-3 font-medium text-right">Action</th>}
                  </tr>
                </thead>
                <tbody>
                  {data.content.map((f) => (
                    <tr key={f.id} className="border-b border-sentinel-800/50 hover:bg-sentinel-850/50 transition-colors">
                      <td className="px-4 py-3 text-sentinel-100">{f.applicationName}</td>
                      <td className="px-4 py-3 text-sentinel-200">{f.title}</td>
                      <td className="px-4 py-3 text-sentinel-400 font-mono text-xs">{f.vulnerabilityId || f.ruleId || '—'}</td>
                      <td className="px-4 py-3"><SeverityBadge v={f.severity} /></td>
                      <td className="px-4 py-3 text-sentinel-400 text-xs">{f.scanner}</td>
                      <td className="px-4 py-3 text-sentinel-400">{f.affectedPackage || '—'}</td>
                      <td className="px-4 py-3 text-sentinel-400 font-mono text-xs">{f.installedVersion || '—'}</td>
                      <td className="px-4 py-3 text-sentinel-400 font-mono text-xs">{f.fixedVersion || '—'}</td>
                      <td className="px-4 py-3"><StatusBadge v={f.status} /></td>
                      <td className="px-4 py-3 text-sentinel-400 text-xs">{f.lastSeen ? new Date(f.lastSeen).toLocaleString() : '—'}</td>
                      {canUpdate && (
                        <td className="px-4 py-3 text-right">
                          <select
                            value={f.status}
                            disabled={updatingId === f.id}
                            onChange={(e) => handleStatusChange(f.id, e.target.value)}
                            className="input-field text-xs w-auto py-1 inline-block"
                          >
                            {FINDING_STATUSES.map((s) => <option key={s} value={s}>{s}</option>)}
                          </select>
                        </td>
                      )}
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
