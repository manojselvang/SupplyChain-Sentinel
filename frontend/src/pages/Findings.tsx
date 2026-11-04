import PageHeader from '../components/PageHeader'

export default function Findings() {
  return (
    <div>
      <PageHeader
        title="Security Findings"
        subtitle="Normalized vulnerabilities and security issues across all scanners"
        actions={
          <div className="flex items-center gap-2">
            <select className="input-field text-sm w-auto">
              <option value="">All Severities</option>
              <option value="CRITICAL">Critical</option>
              <option value="HIGH">High</option>
              <option value="MEDIUM">Medium</option>
              <option value="LOW">Low</option>
            </select>
            <select className="input-field text-sm w-auto">
              <option value="">All Scanners</option>
              <option value="GITLEAKS">Gitleaks</option>
              <option value="SONARQUBE">SonarQube</option>
              <option value="OWASP_DC">OWASP Dependency-Check</option>
              <option value="TRIVY">Trivy</option>
            </select>
          </div>
        }
      />

      <div className="card overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-sentinel-850 border-b border-sentinel-800">
            <tr className="text-left text-sentinel-400">
              <th className="px-4 py-3 font-medium">Finding</th>
              <th className="px-4 py-3 font-medium">Application</th>
              <th className="px-4 py-3 font-medium">Scanner</th>
              <th className="px-4 py-3 font-medium">Category</th>
              <th className="px-4 py-3 font-medium">Severity</th>
              <th className="px-4 py-3 font-medium">Status</th>
              <th className="px-4 py-3 font-medium">Detected</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td colSpan={7} className="px-4 py-12 text-center text-sentinel-500">
                No security findings yet. Finding management and scanner ingestion will be implemented in Phase 6.
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  )
}
