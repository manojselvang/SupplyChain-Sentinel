import { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import ReactECharts from 'echarts-for-react'
import PageHeader from '../components/PageHeader'
import { getDashboardSummary } from '../api/dashboard'
import { extractApiError } from '../api/client'
import type { DashboardSummary, DistributionEntry } from '../types'

// ── KPI card ────────────────────────────────────────────────

function KpiCard({ label, value, color }: { label: string; value: number; color: string }) {
  return (
    <div className="card p-5">
      <div className="flex items-center justify-between mb-3">
        <span className="text-sm text-sentinel-400">{label}</span>
        <span className={`w-3 h-3 rounded-full ${color}`} />
      </div>
      <span className="text-2xl font-semibold text-sentinel-100">{value}</span>
    </div>
  )
}

// ── Chart option builders ───────────────────────────────────

const CHART_TEXT_COLOR = '#8a96b0'
const CHART_GRID_COLOR = '#243049'

function pieOption(data: DistributionEntry[], colors: Record<string, string>) {
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, textStyle: { color: CHART_TEXT_COLOR }, itemWidth: 12, itemHeight: 12 },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      avoidLabelOverlap: true,
      label: { show: false },
      emphasis: { label: { show: true, color: CHART_TEXT_COLOR } },
      data: data.map((d) => ({
        name: d.key,
        value: d.count,
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
      type: 'category',
      data: data.map((d) => d.key),
      axisLabel: { color: CHART_TEXT_COLOR, rotate: data.length > 4 ? 30 : 0 },
      axisLine: { lineStyle: { color: CHART_GRID_COLOR } },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: CHART_TEXT_COLOR },
      splitLine: { lineStyle: { color: CHART_GRID_COLOR } },
    },
    series: [{
      type: 'bar',
      data: data.map((d) => d.count),
      itemStyle: { color, borderRadius: [4, 4, 0, 0] },
      barMaxWidth: 40,
    }],
  }
}

const CRITICALITY_COLORS: Record<string, string> = {
  CRITICAL: '#dc2626', HIGH: '#ea580c', MEDIUM: '#f59e0b', LOW: '#3b82f6',
}

const LIFECYCLE_COLORS: Record<string, string> = {
  ACTIVE: '#22c55e', DEPRECATED: '#f59e0b', PLANNING: '#3b82f6', RETIRED: '#64748b',
}

const HOSTING_COLORS: Record<string, string> = {
  AWS: '#f97316', AZURE: '#3b82f6', GCP: '#22c55e', ON_PREMISES: '#64748b', HYBRID: '#8b5cf6',
}

// ── Badge helpers ───────────────────────────────────────────

function CriticalityBadge({ value }: { value: string }) {
  const cls = value === 'CRITICAL' ? 'badge-critical'
    : value === 'HIGH' ? 'badge-high'
    : value === 'MEDIUM' ? 'badge-medium' : 'badge-low'
  return <span className={`badge ${cls}`}>{value}</span>
}

function StatusBadge({ value }: { value: string }) {
  const cls = value === 'ACTIVE' ? 'bg-green-500/15 text-green-400 border border-green-500/30'
    : value === 'DEPRECATED' ? 'bg-amber-500/15 text-amber-400 border border-amber-500/30'
    : value === 'PLANNING' ? 'bg-blue-500/15 text-blue-400 border border-blue-500/30'
    : 'bg-sentinel-700/40 text-sentinel-400 border border-sentinel-600/50'
  return <span className={`badge ${cls}`}>{value}</span>
}

// ── Chart card wrapper ──────────────────────────────────────

function ChartCard({ title, option, height = 280 }: { title: string; option: object; height?: number }) {
  return (
    <div className="card p-5">
      <h3 className="text-sm font-medium text-sentinel-200 mb-4">{title}</h3>
      <ReactECharts option={option} style={{ height }} opts={{ renderer: 'svg' }} />
    </div>
  )
}

// ── Main component ──────────────────────────────────────────

export default function Dashboard() {
  const navigate = useNavigate()
  const [data, setData] = useState<DashboardSummary | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetchData = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const result = await getDashboardSummary()
      setData(result)
    } catch (err) {
      setError(extractApiError(err as Parameters<typeof extractApiError>[0]).message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchData()
  }, [fetchData])

  return (
    <div>
      <PageHeader
        title="Overview"
        subtitle="Executive dashboard — application inventory metrics"
        actions={
          <button onClick={fetchData} className="btn-secondary text-sm" title="Refresh">
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M16.023 9.348h4.992v-.001M2.985 19.644v-4.992m0 0h4.992m-4.993 0l3.181 3.183a8.25 8.25 0 0011.667 0l3.181-3.183m-4.991-2.698v-4.992m0 0h-4.992m4.992 0l-3.181-3.183a8.25 8.25 0 00-11.667 0l-3.18 3.183" />
            </svg>
            <span className="hidden sm:inline">Refresh</span>
          </button>
        }
      />

      {/* Error state */}
      {error && (
        <div className="card p-4 mb-4 bg-red-500/10 border-red-500/30">
          <p className="text-sm text-red-400">{error}</p>
        </div>
      )}

      {/* Loading state */}
      {loading ? (
        <div className="flex items-center justify-center py-16">
          <div className="w-8 h-8 border-2 border-accent-500 border-t-transparent rounded-full animate-spin" />
        </div>
      ) : !data ? (
        <div className="card p-6 text-center text-sentinel-500">
          No data available.
        </div>
      ) : (
        <>
          {/* KPI cards */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
            <KpiCard label="Total Applications" value={data.totalApplications} color="bg-blue-500" />
            <KpiCard label="Active Applications" value={data.activeApplications} color="bg-green-500" />
            <KpiCard label="Critical Applications" value={data.criticalApplications} color="bg-red-500" />
            <KpiCard label="Internet-facing Apps" value={data.internetFacingApplications} color="bg-accent-500" />
          </div>

          {/* Charts */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 mb-6">
            <ChartCard
              title="Criticality Distribution"
              option={pieOption(data.criticalityDistribution, CRITICALITY_COLORS)}
            />
            <ChartCard
              title="Lifecycle Status Distribution"
              option={pieOption(data.lifecycleStatusDistribution, LIFECYCLE_COLORS)}
            />
            <ChartCard
              title="Hosting Environment Distribution"
              option={pieOption(data.hostingEnvironmentDistribution, HOSTING_COLORS)}
            />
            <ChartCard
              title="Applications by Business Unit"
              option={barOption(data.businessUnitDistribution, '#f97316')}
            />
          </div>

          {/* Recent applications */}
          <div className="card overflow-hidden mb-6">
            <h3 className="text-sm font-medium text-sentinel-200 px-5 py-4 border-b border-sentinel-800">
              Recent Applications
            </h3>
            {data.recentApplications.length === 0 ? (
              <div className="px-5 py-12 text-center text-sentinel-500">
                No applications registered yet.
              </div>
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
                      <tr
                        key={app.id}
                        className="border-b border-sentinel-800/50 hover:bg-sentinel-850/50 transition-colors cursor-pointer"
                        onClick={() => navigate(`/applications/${app.id}`)}
                      >
                        <td className="px-4 py-3 text-sentinel-100 font-medium">{app.name}</td>
                        <td className="px-4 py-3 text-sentinel-400 font-mono text-xs">{app.applicationKey}</td>
                        <td className="px-4 py-3"><CriticalityBadge value={app.criticality} /></td>
                        <td className="px-4 py-3"><StatusBadge value={app.lifecycleStatus} /></td>
                        <td className="px-4 py-3 text-sentinel-400 text-xs">
                          {app.updatedAt ? new Date(app.updatedAt).toLocaleString() : '—'}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          {/* Future capabilities note */}
          <div className="p-3 bg-sentinel-850 border border-sentinel-700 rounded-lg">
            <p className="text-xs text-sentinel-400">
              Dashboard currently displays application inventory metrics. Pipeline, vulnerability,
              SBOM and release-risk metrics will be added in upcoming phases as those modules are implemented.
            </p>
          </div>
        </>
      )}
    </div>
  )
}
