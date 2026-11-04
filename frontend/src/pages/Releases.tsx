import PageHeader from '../components/PageHeader'

export default function Releases() {
  return (
    <div>
      <PageHeader
        title="Release Management"
        subtitle="Release candidates, risk assessments and approval workflows"
        actions={<button className="btn-primary text-sm">+ Create Release</button>}
      />

      {/* Status summary */}
      <div className="grid grid-cols-1 sm:grid-cols-4 gap-4 mb-6">
        <div className="card p-4">
          <span className="text-xs text-sentinel-400">Approved</span>
          <p className="text-xl font-semibold text-green-400 mt-1">—</p>
        </div>
        <div className="card p-4">
          <span className="text-xs text-sentinel-400">Blocked</span>
          <p className="text-xl font-semibold text-red-400 mt-1">—</p>
        </div>
        <div className="card p-4">
          <span className="text-xs text-sentinel-400">Pending</span>
          <p className="text-xl font-semibold text-amber-400 mt-1">—</p>
        </div>
        <div className="card p-4">
          <span className="text-xs text-sentinel-400">Conditionally Approved</span>
          <p className="text-xl font-semibold text-sentinel-200 mt-1">—</p>
        </div>
      </div>

      <div className="card overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-sentinel-850 border-b border-sentinel-800">
            <tr className="text-left text-sentinel-400">
              <th className="px-4 py-3 font-medium">Release</th>
              <th className="px-4 py-3 font-medium">Application</th>
              <th className="px-4 py-3 font-medium">Version</th>
              <th className="px-4 py-3 font-medium">Risk Score</th>
              <th className="px-4 py-3 font-medium">Decision</th>
              <th className="px-4 py-3 font-medium">Approvals</th>
              <th className="px-4 py-3 font-medium">Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td colSpan={7} className="px-4 py-12 text-center text-sentinel-500">
                No releases yet. Release management and the risk engine will be implemented in Phases 8 &amp; 9.
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  )
}
