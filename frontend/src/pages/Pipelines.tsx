import PageHeader from '../components/PageHeader'

export default function Pipelines() {
  return (
    <div>
      <PageHeader
        title="CI/CD Pipelines"
        subtitle="Pipeline executions, scanner status and build metadata"
      />

      <div className="card overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-sentinel-850 border-b border-sentinel-800">
            <tr className="text-left text-sentinel-400">
              <th className="px-4 py-3 font-medium">Application</th>
              <th className="px-4 py-3 font-medium">Branch</th>
              <th className="px-4 py-3 font-medium">Commit</th>
              <th className="px-4 py-3 font-medium">Version</th>
              <th className="px-4 py-3 font-medium">Status</th>
              <th className="px-4 py-3 font-medium">Scanners</th>
              <th className="px-4 py-3 font-medium">Completed</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td colSpan={7} className="px-4 py-12 text-center text-sentinel-500">
                No pipeline executions yet. Pipeline metadata ingestion will be implemented in Phase 5.
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  )
}
