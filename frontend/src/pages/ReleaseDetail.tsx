import { useState, useEffect, useCallback } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import PageHeader from '../components/PageHeader'
import { getAssessment, submitApproval } from '../api/releaseAssessments'
import { extractApiError } from '../api/client'
import { useAuth } from '../context/AuthContext'
import type { ReleaseAssessment } from '../types'

function DecisionBadge({ v }: { v: string }) {
  const c = v === 'PASS' ? 'bg-green-500/15 text-green-400 border border-green-500/30'
    : v === 'WARN' ? 'bg-amber-500/15 text-amber-400 border border-amber-500/30'
    : 'bg-red-500/15 text-red-400 border border-red-500/30'
  return <span className={`badge ${c} font-bold text-sm`}>{v}</span>
}

export default function ReleaseDetail() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { user, hasAnyRole } = useAuth()
  const canApprove = hasAnyRole('ADMIN', 'RELEASE_MANAGER')

  const [assessment, setAssessment] = useState<ReleaseAssessment | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [approvalDialogOpen, setApprovalDialogOpen] = useState(false)
  const [approvalDecision, setApprovalDecision] = useState<'APPROVED' | 'REJECTED'>('APPROVED')
  const [justification, setJustification] = useState('')
  const [approving, setApproving] = useState(false)
  const [approvalError, setApprovalError] = useState<string | null>(null)

  const fetchData = useCallback(async () => {
    if (!id) return
    setLoading(true); setError(null)
    try { setAssessment(await getAssessment(id)) }
    catch (err) { setError(extractApiError(err as Parameters<typeof extractApiError>[0]).message) }
    finally { setLoading(false) }
  }, [id])

  useEffect(() => { fetchData() }, [fetchData])

  const handleApproval = async () => {
    if (!id) return
    setApproving(true); setApprovalError(null)
    try {
      await submitApproval(id, approvalDecision, justification || undefined)
      setApprovalDialogOpen(false)
      fetchData()
    } catch (err) {
      setApprovalError(extractApiError(err as Parameters<typeof extractApiError>[0]).message)
      setApproving(false)
    }
  }

  const isOwnAssessment = assessment?.requestedBy === user?.username
  const isCompleted = assessment?.assessmentStatus === 'READY_FOR_DEPLOYMENT' || assessment?.assessmentStatus === 'REJECTED'
  const isBlock = assessment?.decision === 'BLOCK'
  const showApproval = canApprove && !isOwnAssessment && !isCompleted && !isBlock

  const parseJson = (s: string | null | undefined) => {
    try { return s ? JSON.parse(s) : null } catch { return null }
  }

  if (loading) return <div className="flex justify-center py-16"><div className="w-8 h-8 border-2 border-accent-500 border-t-transparent rounded-full animate-spin" /></div>
  if (error || !assessment) return (
    <div><PageHeader title="Release Assessment" />
      <div className="card p-6 text-center"><p className="text-sm text-red-400 mb-3">{error || 'Not found'}</p>
        <button onClick={() => navigate('/releases')} className="btn-secondary text-sm">← Back</button></div></div>
  )

  const breakdown = parseJson(assessment.scoreBreakdown)
  const evidence = parseJson(assessment.evidenceSnapshot)
  const blocks = parseJson(assessment.hardBlockReasons) as string[] | null

  return (
    <div>
      <PageHeader
        title={`Release Assessment — ${assessment.applicationName}`}
        subtitle={`Policy: ${assessment.policyVersion} · Assessed ${new Date(assessment.assessedAt).toLocaleString()}`}
        actions={
          <div className="flex items-center gap-2">
            <button onClick={() => navigate('/releases')} className="btn-secondary text-sm">← Back</button>
            {showApproval && (
              <>
                <button onClick={() => { setApprovalDecision('APPROVED'); setApprovalDialogOpen(true) }}
                  className="text-sm px-4 py-2 rounded-lg bg-green-700 hover:bg-green-600 text-white font-medium transition-colors">
                  Approve
                </button>
                <button onClick={() => { setApprovalDecision('REJECTED'); setApprovalDialogOpen(true) }}
                  className="text-sm px-4 py-2 rounded-lg bg-red-700 hover:bg-red-600 text-white font-medium transition-colors">
                  Reject
                </button>
              </>
            )}
          </div>
        }
      />

      {/* Release summary */}
      <div className="card p-5 mb-4">
        <h3 className="text-sm font-medium text-sentinel-200 mb-4">Release Summary</h3>
        <div className="flex flex-wrap gap-6 items-center">
          <div>
            <span className="text-xs text-sentinel-400">Risk Score</span>
            <p className={`text-4xl font-bold mt-1 ${assessment.riskScore >= 50 ? 'text-red-400' : assessment.riskScore >= 25 ? 'text-amber-400' : 'text-green-400'}`}>
              {assessment.riskScore}<span className="text-lg text-sentinel-500">/100</span>
            </p>
          </div>
          <div><span className="text-xs text-sentinel-400">Decision</span><div className="mt-1"><DecisionBadge v={assessment.decision} /></div></div>
          <div><span className="text-xs text-sentinel-400">Status</span><p className="text-sm text-sentinel-200 mt-1">{assessment.assessmentStatus.replace('_', ' ')}</p></div>
          <div><span className="text-xs text-sentinel-400">Artifact Digest</span><p className="text-xs font-mono text-sentinel-400 mt-1">{assessment.artifactDigest ? assessment.artifactDigest.substring(0, 20) + '…' : '—'}</p></div>
          <div><span className="text-xs text-sentinel-400">Requested By</span><p className="text-sm text-sentinel-200 mt-1">{assessment.requestedBy || '—'}</p></div>
        </div>
      </div>

      {/* Hard block reasons */}
      {blocks && blocks.length > 0 && (
        <div className="card p-5 mb-4 bg-red-500/5 border-red-500/30">
          <h3 className="text-sm font-medium text-red-400 mb-3">🚫 Hard Block Conditions</h3>
          <ul className="space-y-1">
            {blocks.map((b, i) => <li key={i} className="text-sm text-red-300 flex items-start gap-2"><span className="text-red-500 mt-0.5">▪</span>{b}</li>)}
          </ul>
        </div>
      )}

      {/* Risk breakdown */}
      {breakdown && (
        <div className="card p-5 mb-4">
          <h3 className="text-sm font-medium text-sentinel-200 mb-4">Risk Breakdown</h3>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
            <div className="bg-sentinel-850 rounded-lg p-3"><span className="text-xs text-sentinel-400">Criticality</span><p className="text-xl font-bold text-accent-400 mt-1">+{breakdown.criticalityPoints ?? 0}</p><p className="text-xs text-sentinel-500">{breakdown.applicationCriticality}</p></div>
            <div className="bg-sentinel-850 rounded-lg p-3"><span className="text-xs text-sentinel-400">Internet Exposure</span><p className="text-xl font-bold text-accent-400 mt-1">+{breakdown.internetFacingPoints ?? 0}</p></div>
            <div className="bg-sentinel-850 rounded-lg p-3"><span className="text-xs text-sentinel-400">Vulnerabilities</span><p className="text-xl font-bold text-accent-400 mt-1">+{breakdown.vulnerabilityPoints ?? 0}</p></div>
            <div className="bg-sentinel-850 rounded-lg p-3"><span className="text-xs text-sentinel-400">Total Score</span><p className={`text-xl font-bold mt-1 ${breakdown.totalScore >= 50 ? 'text-red-400' : breakdown.totalScore >= 25 ? 'text-amber-400' : 'text-green-400'}`}>{breakdown.totalScore ?? 0}</p></div>
          </div>
        </div>
      )}

      {/* Evidence snapshot */}
      {evidence && (
        <div className="card p-5 mb-4">
          <h3 className="text-sm font-medium text-sentinel-200 mb-4">Evidence at Assessment Time</h3>
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-3 text-sm">
            {[['Pipeline Status', evidence.pipelineStatus], ['Has Security Scan', evidence.hasSecurityScan ? '✓' : '✗'], ['Has SBOM', evidence.hasSbom ? '✓' : '✗'], ['SBOM Components', evidence.sbomComponentCount], ['Cosign Verified', evidence.hasCosignVerification ? '✓' : '✗'], ['Active Findings', evidence.activeFindingsCount], ['Critical Findings', evidence.criticalFindings], ['Accepted Findings', evidence.acceptedFindingsCount]].map(([label, value]) => (
              <div key={label as string} className="bg-sentinel-850 rounded-lg p-3">
                <span className="text-xs text-sentinel-400">{label}</span>
                <p className={`text-sm font-medium mt-1 ${value === '✓' ? 'text-green-400' : value === '✗' ? 'text-red-400' : 'text-sentinel-200'}`}>{String(value ?? '—')}</p>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Approval history */}
      <div className="card p-5 mb-4">
        <h3 className="text-sm font-medium text-sentinel-200 mb-4">Approval History</h3>
        {assessment.approvals.length === 0 ? (
          <p className="text-sm text-sentinel-500">No approvals yet.</p>
        ) : (
          <div className="space-y-2">
            {assessment.approvals.map(ap => (
              <div key={ap.id} className="bg-sentinel-850 rounded-lg p-3 flex flex-wrap gap-4 text-sm">
                <span className={`badge font-medium ${ap.decision === 'APPROVED' ? 'bg-green-500/15 text-green-400 border border-green-500/30' : 'bg-red-500/15 text-red-400 border border-red-500/30'}`}>{ap.decision}</span>
                {ap.justification && <span className="text-sentinel-300">"{ap.justification}"</span>}
                <span className="text-sentinel-400 text-xs">{new Date(ap.approvedAt).toLocaleString()}</span>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Audit history */}
      <div className="card p-5 mb-4">
        <h3 className="text-sm font-medium text-sentinel-200 mb-4">Audit Trail</h3>
        {assessment.auditHistory.length === 0 ? (
          <p className="text-sm text-sentinel-500">No audit events.</p>
        ) : (
          <div className="space-y-1">
            {assessment.auditHistory.map(ev => (
              <div key={ev.id} className="flex items-start gap-3 text-sm py-1">
                <span className="text-sentinel-500 text-xs w-40 shrink-0">{new Date(ev.occurredAt).toLocaleString()}</span>
                <span className="badge badge-info">{ev.actorRole || 'SYSTEM'}</span>
                <span className="text-sentinel-300">{ev.action}</span>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Approval dialog */}
      {approvalDialogOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div className="fixed inset-0 bg-black/60" onClick={() => setApprovalDialogOpen(false)} />
          <div className="relative w-full max-w-md bg-sentinel-900 border border-sentinel-800 rounded-lg shadow-xl p-6">
            <h2 className="text-lg font-semibold text-sentinel-100 mb-4">
              {approvalDecision === 'APPROVED' ? 'Approve Release' : 'Reject Release'}
            </h2>
            {(assessment.decision === 'WARN' && approvalDecision === 'APPROVED') && (
              <p className="text-xs text-amber-400 bg-amber-500/10 px-3 py-2 rounded-lg mb-3">
                Justification is required when approving a WARN assessment.
              </p>
            )}
            <textarea value={justification} onChange={e => setJustification(e.target.value)}
              className="input-field w-full min-h-[80px]" placeholder="Justification..." />
            {approvalError && <p className="text-sm text-red-400 mt-2">{approvalError}</p>}
            <div className="flex justify-end gap-2 mt-4">
              <button onClick={() => setApprovalDialogOpen(false)} disabled={approving} className="btn-secondary text-sm">Cancel</button>
              <button onClick={handleApproval} disabled={approving}
                className={`text-sm px-4 py-2 rounded-lg font-medium text-white transition-colors ${approvalDecision === 'APPROVED' ? 'bg-green-700 hover:bg-green-600' : 'bg-red-700 hover:bg-red-600'}`}>
                {approving ? 'Submitting...' : approvalDecision === 'APPROVED' ? 'Confirm Approval' : 'Confirm Rejection'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
