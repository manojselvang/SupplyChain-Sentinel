import PageHeader from '../components/PageHeader'

export default function AuditLogs() {
  return (
    <div>
      <PageHeader
        title="Audit Logs"
        subtitle="Append-only audit trail of all platform actions and events"
        actions={
          <button className="btn-secondary text-sm">
            Export
          </button>
        }
      />

      <div className="card overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-sentinel-850 border-b border-sentinel-800">
            <tr className="text-left text-sentinel-400">
              <th className="px-4 py-3 font-medium">Timestamp</th>
              <th className="px-4 py-3 font-medium">Actor</th>
              <th className="px-4 py-3 font-medium">Action</th>
              <th className="px-4 py-3 font-medium">Entity</th>
              <th className="px-4 py-3 font-medium">Details</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td colSpan={5} className="px-4 py-12 text-center text-sentinel-500">
                No audit records yet. Audit trail will be implemented in Phase 11.
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  )
}
