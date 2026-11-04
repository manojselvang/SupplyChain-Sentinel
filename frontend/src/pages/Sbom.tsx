import PageHeader from '../components/PageHeader'

export default function Sbom() {
  return (
    <div>
      <PageHeader
        title="SBOM Management"
        subtitle="Software Bill of Materials — component inventory and vulnerability tracking"
        actions={<button className="btn-primary text-sm">+ Upload SBOM</button>}
      />

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4 mb-6">
        <div className="card p-5">
          <span className="text-sm text-sentinel-400">SBOM Documents</span>
          <p className="text-2xl font-semibold text-sentinel-100 mt-2">—</p>
        </div>
        <div className="card p-5">
          <span className="text-sm text-sentinel-400">Tracked Components</span>
          <p className="text-2xl font-semibold text-sentinel-100 mt-2">—</p>
        </div>
        <div className="card p-5">
          <span className="text-sm text-sentinel-400">Vulnerable Components</span>
          <p className="text-2xl font-semibold text-sentinel-100 mt-2">—</p>
        </div>
      </div>

      <div className="card overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-sentinel-850 border-b border-sentinel-800">
            <tr className="text-left text-sentinel-400">
              <th className="px-4 py-3 font-medium">Application</th>
              <th className="px-4 py-3 font-medium">Version</th>
              <th className="px-4 py-3 font-medium">Components</th>
              <th className="px-4 py-3 font-medium">Format</th>
              <th className="px-4 py-3 font-medium">Uploaded</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td colSpan={5} className="px-4 py-12 text-center text-sentinel-500">
                No SBOM documents uploaded yet. SBOM management will be implemented in Phase 7.
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  )
}
