import { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import ReactECharts from 'echarts-for-react'
import PageHeader from '../components/PageHeader'
import { getDashboardSummary } from '../api/dashboard'
import { extractApiError } from '../api/client'
import type { DashboardSummary, DistributionEntry, RecentFinding, RecentSbom, RecentArtifactVerification } from '../types'

// ── KPI card ─────────────────────────────────────────────────

function KpiCard({ label, value, color, sub }: {
  label: string; value: number | string; color: string; sub?: string
}) {
  return (
    <div className="card p-5">
      <div className="flex items-center justify-between mb-3">
        <span className="text-sm text-sentinel-400">{label}</span>
        <span className={`w-3 h-3 rounded-full ${color}`} />
      </div>
      <div className="flex items-end gap-2">
        <span className="text-2xl font-semibold text-sentinel-100">{value}</span>
        {sub && <span className="text-xs text-sentinel-400 mb-1">{sub}</span>}
      </div>
    </div>
  )
}

// ── Chart helpers ─────────────────────────────────────────────

const CT = '#8a96b0'  // chart text colour
const CG = '#243049'  // chart grid colour

function pieOption(data: DistributionEntry[], colors: Record<string, string>) {
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, textStyle: { color: CT }, itemWidth: 12, itemHeight: 12 },
    series: [{
      type: 'pie', radius: ['40%', '70%'], avoidLabelOverlap: true,
      label: { show: false },
      emphasis: { label: { show: true, color: CT } },
      data: data.map((d) => ({
        name: d.key, value: d.count,
        itemStyle: { color: colors[d.key] ?? '#64748b' },
      })),
    }],
  }
}

function barOption(data: DistributionEntry[], color: string) {
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: {
      type: 'category', data: data.map((d) => d.key),
      axisLabel: { color: CT, rotate: data.length > 4 ? 30 : 0 },
      axisLine: { lineStyle: { color: CG } },
    },
    yAxis: {
      type: 'value', minInterval: 1,
      axisLabel: { color: CT }, splitLine: { lineStyle: { color: CG } },
    },
    series: [{ type: 'bar', data: data.map((d) => d.count), itemStyle: { color, borderRadius: [4, 4, 0, 0] }, barMaxWidth: 40 }],
  }
}

const CRIT_COLORS: Record<string, string> = { CRITICAL: '#dc2626', HIGH: '#ea580c', MEDIUM: '#f59e0b', LOW: '#3b82f6' }
const LC_COLORS: Record<string, string> = { ACTIVE: '#22c55e', DEPRECATED: '#f59e0b', PLANNING: '#3b82f6', RETIRED: '#64748b' }
const HOST_COLORS: Record<string, string> = { AWS: '#f97316', AZURE: '#3b82f6', GCP: '#22c55e', ON_PREMISES: '#64748b', HYBRID: '#8b5cf6' }

// ── Badge helpers ─────────────────────────────────────────────

function CritBadge({ v }: { v: string }) {
  const c = v === 'CRITICAL' ? 'badge-critical' : v === 'HIGH' ? 'badge-high' : v === 'MEDIUM' ? 'badge-medium' : 'badge-low'
  return <span className={`badge ${c}`}>{v}</span>
}
function LcBadge({ v }: { v: string }) {
  const c = v === 'ACTIVE' ? 'bg-green-500/15 text-green-400 border border-green-500/30'
    : v === 'DEPRECATED' ? 'bg-amber-500/15 text-amber-400 border border-amber-500/30'
    : v === 'PLANNING' ? 'bg-blue-500/15 text-blue-400 border border-blue-500/30'
    : 'bg-sentinel-700/40 text-sentinel-400 border border-sentinel-600/50'
  return <span className={`badge ${c}`}>{v}</span>
}
function PipeStatusBadge({ v }: { v: string }) {
  const c = v === 'SUCCESS' ? 'bg-green-500/15 text-green-400 border border-green-500/30'
    : v === 'FAILED' ? 'bg-red-500/15 text-red-400 border border-red-500/30'
    : v === 'RUNNING' ? 'bg-blue-500/15 text-blue-400 border border-blue-500/30'
    : 'bg-sentinel-700/40 text-sentinel-400 border border-sentinel-600/50'
  return <span className={`badge ${c}`}>{v}</span>
}

function ChartCard({ title, option, h = 280 }: { title: string; option: object; h?: number }) {
  return (
    <div className="card p-5">
      <h3 className="text-sm font-medium text-sentinel-200 mb-4">{title}</h3>
      <ReactECharts option={option} style={{ height: h }} opts={{ renderer: 'svg' }} />
    </div>
  )
}

// ── Main component ────────────────────────────────────────────

export default function Dashboard() {
  const navigate = useNavigate()
  const [data, setData] = useState<DashboardSummary | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetchData = useCallback(async () => {
    setLoading(true)
    setError(null)
    try { setData(await getDashboardSummary()) }
    catch (err) { setError(extractApiError(err as Parameters<typeof extractApiError>[0]).message) }
    finally { setLoading(false) }
  }, [])

  useEffect(() => { fetchData() }, [fetchData])

  return (
    <div>
      <PageHeader
        title="Overview"
        subtitle="Executive dashboard — application inventory and pipeline metrics"
        actions={
          <button onClick={fetchData} className="btn-secondary text-sm">
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M16.023 9.348h4.992v-.001M2.985 19.644v-4.992m0 0h4.992m-4.993 0l3.181 3.183a8.25 8.25 0 0011.667 0l3.181-3.183m-4.991-2.698v-4.992m0 0h-4.992m4.992 0l-3.181-3.183a8.25 8.25 0 00-11.667 0l-3.18 3.183" />
            </svg>
            <span className="hidden sm:inline">Refresh</span>
          </button>
        }
      />

      {error && <div className="card p-4 mb-4 bg-red-500/10 border-red-500/30"><p className="text-sm text-red-400">{error}</p></div>}

      {loading ? (
        <div className="flex items-center justify-center py-16">
          <div className="w-8 h-8 border-2 border-accent-500 border-t-transparent rounded-full animate-spin" />
        </div>
      ) : !data ? (
        <div className="card p-6 text-center text-sentinel-500">No data available.</div>
      ) : (
        <>
          {/* ── Application KPIs ─────────────────────────────── */}
          <p className="text-xs text-sentinel-500 uppercase tracking-widest mb-2">Application Inventory</p>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
            <KpiCard label="Total Applications" value={data.totalApplications} color="bg-blue-500" />
            <KpiCard label="Active Applications" value={data.activeApplications} color="bg-green-500" />
            <KpiCard label="Critical Applications" value={data.criticalApplications} color="bg-red-500" />
            <KpiCard label="Internet-facing Apps" value={data.internetFacingApplications} color="bg-accent-500" />
          </div>

          {/* ── Pipeline KPIs ─────────────────────────────────── */}
          <p className="text-xs text-sentinel-500 uppercase tracking-widest mb-2">CI/CD Pipelines</p>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
            <KpiCard label="Total Pipeline Runs" value={data.totalPipelineRuns} color="bg-blue-400" />
            <KpiCard label="Successful Runs" value={data.successfulPipelineRuns} color="bg-green-500" />
            <KpiCard label="Failed Runs" value={data.failedPipelineRuns} color="bg-red-500" />
            <KpiCard
              label="Success Rate"
              value={`${Math.round(data.pipelineSuccessRate)}%`}
              color={data.pipelineSuccessRate >= 80 ? 'bg-green-500' : data.pipelineSuccessRate >= 50 ? 'bg-amber-500' : 'bg-red-500'}
            />
          </div>

          {/* ── Security Findings KPIs ─────────────────────── */}
          <p className="text-xs text-sentinel-500 uppercase tracking-widest mb-2">Security Findings</p>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
            <KpiCard label="Open Findings" value={data.openFindings} color="bg-red-500" />
            <KpiCard label="Critical Open" value={data.criticalOpenFindings} color="bg-red-600" />
            <KpiCard label="High Open" value={data.highOpenFindings} color="bg-accent-500" />
            <KpiCard label="By Severity" value={data.openFindingsBySeverity.length} color="bg-sentinel-400" sub="distinct levels" />
          </div>

          {/* ── Charts ───────────────────────────────────────── */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 mb-6">
            <ChartCard title="Criticality Distribution" option={pieOption(data.criticalityDistribution, CRIT_COLORS)} />
            <ChartCard title="Lifecycle Status Distribution" option={pieOption(data.lifecycleStatusDistribution, LC_COLORS)} />
            <ChartCard title="Hosting Environment Distribution" option={pieOption(data.hostingEnvironmentDistribution, HOST_COLORS)} />
            <ChartCard title="Applications by Business Unit" option={barOption(data.businessUnitDistribution, '#f97316')} />
          </div>

          {/* ── Recent applications ───────────────────────────── */}
          <div className="card overflow-hidden mb-4">
            <h3 className="text-sm font-medium text-sentinel-200 px-5 py-4 border-b border-sentinel-800">Recent Applications</h3>
            {data.recentApplications.length === 0 ? (
              <div className="px-5 py-8 text-center text-sentinel-500">No applications registered yet.</div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead className="bg-sentinel-850 border-b border-sentinel-800">
                    <tr className="text-left text-sentinel-400">
                      <th className="px-4 py-3 font-medium">Application</th>
                      <th className="px-4 py-3 font-medium">Key</th>
                      <th className="px-4 py-3 font-medium">Criticality</th>
                      <th className="px-4 py-3 font-medium">Lifecycle</th>
                      <th className="px-4 py-3 font-medium">Last Updated</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.recentApplications.map((app) => (
                      <tr key={app.id} className="border-b border-sentinel-800/50 hover:bg-sentinel-850/50 transition-colors cursor-pointer"
                          onClick={() => navigate(`/applications/${app.id}`)}>
                        <td className="px-4 py-3 text-sentinel-100 font-medium">{app.name}</td>
                        <td className="px-4 py-3 text-sentinel-400 font-mono text-xs">{app.applicationKey}</td>
                        <td className="px-4 py-3"><CritBadge v={app.criticality} /></td>
                        <td className="px-4 py-3"><LcBadge v={app.lifecycleStatus} /></td>
                        <td className="px-4 py-3 text-sentinel-400 text-xs">{app.updatedAt ? new Date(app.updatedAt).toLocaleString() : '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          {/* ── Recent pipeline runs ──────────────────────────── */}
          <div className="card overflow-hidden mb-4">
            <h3 className="text-sm font-medium text-sentinel-200 px-5 py-4 border-b border-sentinel-800">Recent Pipeline Runs</h3>
            {data.recentPipelineRuns.length === 0 ? (
              <div className="px-5 py-8 text-center text-sentinel-500">No pipeline runs yet. Configure Jenkins or GitHub Actions integration.</div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead className="bg-sentinel-850 border-b border-sentinel-800">
                    <tr className="text-left text-sentinel-400">
                      <th className="px-4 py-3 font-medium">Application</th>
                      <th className="px-4 py-3 font-medium">Provider</th>
                      <th className="px-4 py-3 font-medium">Workflow</th>
                      <th className="px-4 py-3 font-medium">Branch</th>
                      <th className="px-4 py-3 font-medium">Status</th>
                      <th className="px-4 py-3 font-medium">Started</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.recentPipelineRuns.map((run) => (
                      <tr key={run.id} className="border-b border-sentinel-800/50 hover:bg-sentinel-850/50 transition-colors">
                        <td className="px-4 py-3 text-sentinel-100">{run.applicationName}</td>
                        <td className="px-4 py-3 text-sentinel-400 text-xs">{run.provider.replace('_', ' ')}</td>
                        <td className="px-4 py-3 text-sentinel-300">{run.workflowName || '—'}</td>
                        <td className="px-4 py-3 text-sentinel-400 font-mono text-xs">{run.branchName || '—'}</td>
                        <td className="px-4 py-3"><PipeStatusBadge v={run.status} /></td>
                        <td className="px-4 py-3 text-sentinel-400 text-xs">{run.startedAt ? new Date(run.startedAt).toLocaleString() : '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          {/* ── Recent findings ──────────────────────────────── */}
          <div className="card overflow-hidden mb-4">
            <h3 className="text-sm font-medium text-sentinel-200 px-5 py-4 border-b border-sentinel-800">Recent Security Findings</h3>
            {data.recentFindings.length === 0 ? (
              <div className="px-5 py-8 text-center text-sentinel-500">No security findings yet. Ingest scanner reports to populate findings.</div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead className="bg-sentinel-850 border-b border-sentinel-800">
                    <tr className="text-left text-sentinel-400">
                      <th className="px-4 py-3 font-medium">Application</th>
                      <th className="px-4 py-3 font-medium">Title</th>
                      <th className="px-4 py-3 font-medium">CVE / Rule</th>
                      <th className="px-4 py-3 font-medium">Severity</th>
                      <th className="px-4 py-3 font-medium">Scanner</th>
                      <th className="px-4 py-3 font-medium">Status</th>
                      <th className="px-4 py-3 font-medium">Last Seen</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.recentFindings.map((f: RecentFinding) => (
                      <tr key={f.id} className="border-b border-sentinel-800/50 hover:bg-sentinel-850/50 transition-colors">
                        <td className="px-4 py-3 text-sentinel-100">{f.applicationName}</td>
                        <td className="px-4 py-3 text-sentinel-200">{f.title}</td>
                        <td className="px-4 py-3 text-sentinel-400 font-mono text-xs">{f.vulnerabilityId || '—'}</td>
                        <td className="px-4 py-3"><CritBadge v={f.severity} /></td>
                        <td className="px-4 py-3 text-sentinel-400 text-xs">{f.scanner}</td>
                        <td className="px-4 py-3 text-sentinel-400 text-xs">{f.status}</td>
                        <td className="px-4 py-3 text-sentinel-400 text-xs">{f.lastSeen ? new Date(f.lastSeen).toLocaleString() : '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          {/* ── SBOM & Artifact Integrity KPIs ─────────────── */}
          <p className="text-xs text-sentinel-500 uppercase tracking-widest mb-2">SBOM &amp; Artifact Integrity</p>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
            <KpiCard label="Total SBOM Documents" value={data.totalSbomDocuments} color="bg-blue-400" />
            <KpiCard label="Total Components" value={data.totalSbomComponents} color="bg-sentinel-400" />
            <KpiCard label="Verified Artifacts" value={data.verifiedArtifacts} color="bg-green-500" />
            <KpiCard label="Failed Verifications" value={data.failedArtifactVerifications} color="bg-red-500" />
          </div>

          {/* ── Recent SBOM documents ───────────────────────── */}
          <div className="card overflow-hidden mb-4">
            <h3 className="text-sm font-medium text-sentinel-200 px-5 py-4 border-b border-sentinel-800">Recent SBOM Documents</h3>
            {data.recentSbomDocuments.length === 0 ? (
              <div className="px-5 py-8 text-center text-sentinel-500">No SBOM documents ingested yet.</div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead className="bg-sentinel-850 border-b border-sentinel-800">
                    <tr className="text-left text-sentinel-400">
                      <th className="px-4 py-3 font-medium">Application</th>
                      <th className="px-4 py-3 font-medium">Version</th>
                      <th className="px-4 py-3 font-medium">Components</th>
                      <th className="px-4 py-3 font-medium">Ingested</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.recentSbomDocuments.map((s: RecentSbom) => (
                      <tr key={s.id} className="border-b border-sentinel-800/50 hover:bg-sentinel-850/50">
                        <td className="px-4 py-3 text-sentinel-100">{s.applicationName}</td>
                        <td className="px-4 py-3"><span className="badge badge-info">{s.specVersion || '—'}</span></td>
                        <td className="px-4 py-3 text-accent-400 font-semibold">{s.componentCount}</td>
                        <td className="px-4 py-3 text-sentinel-400 text-xs">{new Date(s.ingestedAt).toLocaleString()}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          {/* ── Recent artifact verifications ────────────────── */}
          <div className="card overflow-hidden mb-4">
            <div className="px-5 py-4 border-b border-sentinel-800">
              <h3 className="text-sm font-medium text-sentinel-200">Recent Artifact Verifications</h3>
              <p className="text-xs text-sentinel-500 mt-0.5">CI-reported Cosign results</p>
            </div>
            {data.recentArtifactVerifications.length === 0 ? (
              <div className="px-5 py-8 text-center text-sentinel-500">No verification results yet.</div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead className="bg-sentinel-850 border-b border-sentinel-800">
                    <tr className="text-left text-sentinel-400">
                      <th className="px-4 py-3 font-medium">Application</th>
                      <th className="px-4 py-3 font-medium">Status</th>
                      <th className="px-4 py-3 font-medium">Mode</th>
                      <th className="px-4 py-3 font-medium">Reported</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.recentArtifactVerifications.map((av: RecentArtifactVerification) => {
                      const cls = av.verificationStatus === 'VERIFIED'
                        ? 'bg-green-500/15 text-green-400 border border-green-500/30'
                        : av.verificationStatus === 'FAILED'
                        ? 'bg-red-500/15 text-red-400 border border-red-500/30'
                        : 'bg-amber-500/15 text-amber-400 border border-amber-500/30'
                      return (
                        <tr key={av.id} className="border-b border-sentinel-800/50 hover:bg-sentinel-850/50">
                          <td className="px-4 py-3 text-sentinel-100">{av.applicationName}</td>
                          <td className="px-4 py-3"><span className={`badge ${cls}`}>{av.verificationStatus.replace('_', ' ')}</span></td>
                          <td className="px-4 py-3"><span className="badge badge-info">{av.verificationMode}</span></td>
                          <td className="px-4 py-3 text-sentinel-400 text-xs">{new Date(av.reportedAt).toLocaleString()}</td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          {/* ── Future capabilities note ──────────────────────── */}
          <div className="p-3 bg-sentinel-850 border border-sentinel-700 rounded-lg">
            <p className="text-xs text-sentinel-400">
              Release-risk metrics will be added in Phase 8 as the release risk assessment module is implemented.
            </p>
          </div>
        </>
      )}
    </div>
  )
}
