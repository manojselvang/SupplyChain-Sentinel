import PageHeader from '../components/PageHeader'

export default function Applications() {
  return (
    <div>
      <PageHeader
        title="Applications"
        subtitle="Application inventory — register, manage and track security posture"
        actions={<button className="btn-primary text-sm">+ Register Application</button>}
      />

      {/* Table placeholder */}
      <div className="card overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-sentinel-850 border-b border-sentinel-800">
            <tr className="text-left text-sentinel-400">
              <th className="px-4 py-3 font-medium">Application</th>
              <th className="px-4 py-3 font-medium">Owner</th>
              <th className="px-4 py-3 font-medium">Criticality</th>
              <th className="px-4 py-3 font-medium">Repository</th>
              <th className="px-4 py-3 font-medium">Posture</th>
              <th className="px-4 py-3 font-medium">Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td colSpan={6} className="px-4 py-12 text-center text-sentinel-500">
                No applications registered yet. Application CRUD will be implemented in Phase 3.
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  )
}
