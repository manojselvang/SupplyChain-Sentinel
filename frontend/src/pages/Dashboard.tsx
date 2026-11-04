import PageHeader from '../components/PageHeader'

interface MetricCardProps {
  label: string
  value: string
  trend?: string
  color: string
}

function MetricCard({ label, value, trend, color }: MetricCardProps) {
  return (
    <div className="card p-5">
      <div className="flex items-center justify-between mb-3">
        <span className="text-sm text-sentinel-400">{label}</span>
        <span className={`w-3 h-3 rounded-full ${color}`} />
      </div>
      <div className="flex items-end gap-2">
        <span className="text-2xl font-semibold text-sentinel-100">{value}</span>
        {trend && <span className="text-xs text-sentinel-400 mb-1">{trend}</span>}
      </div>
    </div>
  )
}

export default function Dashboard() {
  return (
    <div>
      <PageHeader
        title="Overview"
        subtitle="Executive dashboard — security posture and release pipeline health"
      />

      {/* Metric cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        <MetricCard label="Total Applications" value="—" color="bg-blue-500" />
        <MetricCard label="Total Releases" value="—" color="bg-sentinel-400" />
        <MetricCard label="Blocked Releases" value="—" color="bg-red-500" />
        <MetricCard label="Pending Approvals" value="—" color="bg-amber-500" />
        <MetricCard label="Open Vulnerabilities" value="—" color="bg-accent-500" />
        <MetricCard label="Critical Vulnerabilities" value="—" color="bg-red-600" />
        <MetricCard label="Failed Security Gates" value="—" color="bg-red-400" />
        <MetricCard label="Verified Artifacts" value="—" color="bg-green-500" />
      </div>

      {/* Chart placeholders */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 mb-6">
        <div className="card p-5">
          <h3 className="text-sm font-medium text-sentinel-200 mb-4">Vulnerability Severity Distribution</h3>
          <div className="h-64 flex items-center justify-center text-sentinel-500 text-sm">
            ECharts visualizations will be populated in Phase 4
          </div>
        </div>
        <div className="card p-5">
          <h3 className="text-sm font-medium text-sentinel-200 mb-4">Pipeline Pass/Fail Ratio</h3>
          <div className="h-64 flex items-center justify-center text-sentinel-500 text-sm">
            ECharts visualizations will be populated in Phase 4
          </div>
        </div>
      </div>

      {/* Recent activity placeholder */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <div className="card p-5">
          <h3 className="text-sm font-medium text-sentinel-200 mb-4">Recent Pipeline Executions</h3>
          <p className="text-sm text-sentinel-500">Populated in Phase 5 — CI/CD Pipeline Integration</p>
        </div>
        <div className="card p-5">
          <h3 className="text-sm font-medium text-sentinel-200 mb-4">Latest Security Findings</h3>
          <p className="text-sm text-sentinel-500">Populated in Phase 6 — Security Findings Management</p>
        </div>
      </div>

      {/* Demo data notice */}
      <div className="mt-6 p-3 bg-sentinel-850 border border-sentinel-700 rounded-lg">
        <p className="text-xs text-sentinel-400">
          All dashboard metrics are retrieved from backend APIs. Simulated demo data will be seeded via
          backend database migrations in upcoming phases.
        </p>
      </div>
    </div>
  )
}
