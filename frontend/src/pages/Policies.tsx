import PageHeader from '../components/PageHeader'

export default function Policies() {
  return (
    <div>
      <PageHeader
        title="Security Policies"
        subtitle="Configurable rules for risk scoring, blocking conditions and release gates"
        actions={<button className="btn-primary text-sm">+ New Policy</button>}
      />

      {/* Policy rules overview */}
      <div className="card p-5 mb-6">
        <h3 className="text-sm font-medium text-sentinel-200 mb-4">Current Weights</h3>
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
          {[
            { label: 'Critical Vuln', value: 25 },
            { label: 'High Vuln', value: 12 },
            { label: 'Medium Vuln', value: 5 },
            { label: 'Low Vuln', value: 1 },
            { label: 'Secret Exposure', value: 40 },
            { label: 'Failed Quality Gate', value: 20 },
            { label: 'Missing SBOM', value: 15 },
            { label: 'Invalid Signature', value: 30 },
          ].map((rule) => (
            <div key={rule.label} className="bg-sentinel-850 border border-sentinel-700 rounded-lg p-3">
              <span className="text-xs text-sentinel-400">{rule.label}</span>
              <p className="text-lg font-semibold text-accent-400 mt-1">{rule.value}</p>
            </div>
          ))}
        </div>
        <p className="text-xs text-sentinel-500 mt-3">
          Score is capped at 100. Blocking conditions are enforced independently of the numerical score.
        </p>
      </div>

      <div className="card overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-sentinel-850 border-b border-sentinel-800">
            <tr className="text-left text-sentinel-400">
              <th className="px-4 py-3 font-medium">Policy Version</th>
              <th className="px-4 py-3 font-medium">Status</th>
              <th className="px-4 py-3 font-medium">Mandatory Scanners</th>
              <th className="px-4 py-3 font-medium">Created</th>
              <th className="px-4 py-3 font-medium">Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td colSpan={5} className="px-4 py-12 text-center text-sentinel-500">
                No policies configured yet. Policy management will be implemented in Phase 11.
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  )
}
