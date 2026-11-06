import { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import PageHeader from '../components/PageHeader'
import Pagination from '../components/Pagination'
import { listArtifactVerifications } from '../api/artifactVerifications'
import { extractApiError } from '../api/client'
import { VERIFICATION_STATUSES, VERIFICATION_MODES, type ArtifactVerification, type Page } from '../types'

// ── Status badge ─────────────────────────────────────────────

function VerificationBadge({ status }: { status: string }) {
  const cls =
    status === 'VERIFIED'
      ? 'bg-green-500/15 text-green-400 border border-green-500/30'
      : status === 'FAILED'
      ? 'bg-red-500/15 text-red-400 border border-red-500/30'
      : status === 'MISSING_SIGNATURE'
      ? 'bg-amber-500/15 text-amber-400 border border-amber-500/30'
      : 'bg-sentinel-700/40 text-sentinel-400 border border-sentinel-600/50'
  return <span className={`badge ${cls}`}>{status.replace('_', ' ')}</span>
}

// ── Main page ─────────────────────────────────────────────────

export default function ArtifactVerifications() {
  const navigate = useNavigate()
  const [data, setData] = useState<Page<ArtifactVerification> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [statusFilter, setStatusFilter] = useState('')
  const [modeFilter, setModeFilter] = useState('')
  const [page, setPage] = useState(0)

  const fetchData = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const result = await listArtifactVerifications({
        status: statusFilter || undefined,
        page,
        size: 20,
        sort: 'createdAt,desc',
      })
      setData(result)
    } catch (err) {
      setError(extractApiError(err as Parameters<typeof extractApiError>[0]).message)
    } finally {
      setLoading(false)
    }
  }, [statusFilter, page])

  useEffect(() => { fetchData() }, [fetchData])

  return (
    <div>
      <PageHeader
        title="Artifact Integrity"
        subtitle="CI-reported Cosign verification results — cryptographic verification is performed by the CI pipeline, not this platform"
        actions={
          <button onClick={fetchData} className="btn-secondary text-sm">
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M16.023 9.348h4.992v-.001M2.985 19.644v-4.992m0 0h4.992m-4.993 0l3.181 3.183a8.25 8.25 0 0011.667 0l3.181-3.183m-4.991-2.698v-4.992m0 0h-4.992m4.992 0l-3.181-3.183a8.25 8.25 0 00-11.667 0l-3.18 3.183" />
            </svg>
            <span className="hidden sm:inline">Refresh</span>
          </button>
        }
      />

      {/* Disclaimer banner */}
      <div className="mb-4 p-3 bg-blue-500/10 border border-blue-500/30 rounded-lg">
        <p className="text-xs text-blue-300">
          ⓘ These are <strong>CI-reported</strong> Cosign verification results. The cryptographic signature
          verification is performed by your CI pipeline (Jenkins / GitHub Actions), not by this platform.
        </p>
      </div>

      {/* Filters */}
      <div className="flex flex-col sm:flex-row gap-3 mb-4">
        <select
          value={statusFilter}
          onChange={e => { setStatusFilter(e.target.value); setPage(0) }}
          className="input-field sm:w-48"
        >
          <option value="">All Statuses</option>
          {VERIFICATION_STATUSES.map(s => (
            <option key={s} value={s}>{s.replace('_', ' ')}</option>
          ))}
        </select>
        <select
          value={modeFilter}
          onChange={e => { setModeFilter(e.target.value); setPage(0) }}
          className="input-field sm:w-36"
        >
          <option value="">All Modes</option>
          {VERIFICATION_MODES.map(m => <option key={m} value={m}>{m}</option>)}
        </select>
      </div>

      {error && (
        <div className="card p-4 mb-4 bg-red-500/10 border-red-500/30">
          <p className="text-sm text-red-400">{error}</p>
        </div>
      )}

      <div className="card overflow-hidden">
        {loading ? (
          <div className="flex items-center justify-center py-16">
            <div className="w-8 h-8 border-2 border-accent-500 border-t-transparent rounded-full animate-spin" />
          </div>
        ) : !data || data.content.length === 0 ? (
          <div className="px-4 py-16 text-center text-sentinel-500">
            {statusFilter || modeFilter
              ? 'No records match your filters.'
              : 'No artifact verifications yet. Configure Cosign in your CI pipeline and submit verification results.'}
          </div>
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-sentinel-850 border-b border-sentinel-800">
                  <tr className="text-left text-sentinel-400">
                    <th className="px-4 py-3 font-medium">Application</th>
                    <th className="px-4 py-3 font-medium">Artifact Reference</th>
                    <th className="px-4 py-3 font-medium">Status</th>
                    <th className="px-4 py-3 font-medium">Mode</th>
                    <th className="px-4 py-3 font-medium">Signer / Key</th>
                    <th className="px-4 py-3 font-medium">Cosign</th>
                    <th className="px-4 py-3 font-medium">Reported</th>
                  </tr>
                </thead>
                <tbody>
                  {data.content
                    .filter(v => !modeFilter || v.verificationMode === modeFilter)
                    .map(v => (
                      <tr key={v.id} className="border-b border-sentinel-800/50 hover:bg-sentinel-850/50 transition-colors">
                        <td className="px-4 py-3">
                          <button
                            onClick={() => navigate(`/applications/${v.applicationId}`)}
                            className="text-sentinel-100 hover:text-accent-400 font-medium transition-colors"
                          >
                            {v.applicationName}
                          </button>
                        </td>
                        <td className="px-4 py-3 text-sentinel-400 font-mono text-xs max-w-xs truncate" title={v.artifactReference}>
                          {v.artifactReference}
                        </td>
                        <td className="px-4 py-3"><VerificationBadge status={v.verificationStatus} /></td>
                        <td className="px-4 py-3">
                          <span className="badge badge-info">{v.verificationMode}</span>
                        </td>
                        <td className="px-4 py-3 text-sentinel-400 text-xs max-w-xs truncate" title={v.signerIdentity || v.publicKeyFingerprint || '—'}>
                          {v.signerIdentity
                            ? v.signerIdentity.length > 40 ? v.signerIdentity.substring(0, 40) + '…' : v.signerIdentity
                            : v.publicKeyFingerprint
                            ? v.publicKeyFingerprint.substring(0, 12) + '…'
                            : '—'}
                        </td>
                        <td className="px-4 py-3 text-sentinel-400 font-mono text-xs">{v.cosignVersion || '—'}</td>
                        <td className="px-4 py-3 text-sentinel-400 text-xs">
                          {new Date(v.reportedAt).toLocaleString()}
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
    </div>
  )
}
