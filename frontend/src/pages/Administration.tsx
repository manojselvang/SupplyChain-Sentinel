import PageHeader from '../components/PageHeader'

export default function Administration() {
  return (
    <div>
      <PageHeader
        title="Administration"
        subtitle="User management, role assignment and system configuration"
        actions={<button className="btn-primary text-sm">+ Create User</button>}
      />

      {/* Role summary */}
      <div className="grid grid-cols-2 sm:grid-cols-5 gap-3 mb-6">
        {[
          { name: 'ADMIN', color: 'text-red-400' },
          { name: 'SECURITY_ENGINEER', color: 'text-accent-400' },
          { name: 'DEVELOPER', color: 'text-blue-400' },
          { name: 'RELEASE_MANAGER', color: 'text-green-400' },
          { name: 'AUDITOR', color: 'text-sentinel-300' },
        ].map((role) => (
          <div key={role.name} className="card p-4 text-center">
            <span className={`text-xs font-medium ${role.color}`}>{role.name}</span>
            <p className="text-lg font-semibold text-sentinel-100 mt-1">—</p>
          </div>
        ))}
      </div>

      <div className="card overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-sentinel-850 border-b border-sentinel-800">
            <tr className="text-left text-sentinel-400">
              <th className="px-4 py-3 font-medium">Username</th>
              <th className="px-4 py-3 font-medium">Display Name</th>
              <th className="px-4 py-3 font-medium">Email</th>
              <th className="px-4 py-3 font-medium">Roles</th>
              <th className="px-4 py-3 font-medium">Status</th>
              <th className="px-4 py-3 font-medium">Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td colSpan={6} className="px-4 py-12 text-center text-sentinel-500">
                No users configured yet. User management will be implemented in Phase 2.
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  )
}
