import PageHeader from '../components/PageHeader'

export default function Deployments() {
  return (
    <div>
      <PageHeader
        title="Deployments"
        subtitle="Deployment verification records and artifact integrity tracking"
      />

      <div className="card overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-sentinel-850 border-b border-sentinel-800">
            <tr className="text-left text-sentinel-400">
              <th className="px-4 py-3 font-medium">Release</th>
              <th className="px-4 py-3 font-medium">Environment</th>
              <th className="px-4 py-3 font-medium">Image Digest</th>
              <th className="px-4 py-3 font-medium">Signature</th>
              <th className="px-4 py-3 font-medium">Verified</th>
              <th className="px-4 py-3 font-medium">Deployed</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td colSpan={6} className="px-4 py-12 text-center text-sentinel-500">
                No deployment records yet. Artifact integrity and deployment tracking will be implemented in Phase 10.
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  )
}
