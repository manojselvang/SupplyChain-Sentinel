import { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import PageHeader from '../components/PageHeader'
import Pagination from '../components/Pagination'
import Modal from '../components/Modal'
import { listSboms, getSbomComponents } from '../api/sboms'
import { extractApiError } from '../api/client'
import type { SbomDocument, SbomComponent, Page } from '../types'

// ── Component inventory modal ────────────────────────────────

function ComponentModal({
  sbom, open, onClose,
}: { sbom: SbomDocument | null; open: boolean; onClose: () => void }) {
  const [components, setComponents] = useState<Page<SbomComponent> | null>(null)
  const [loading, setLoading] = useState(false)
  const [nameSearch, setNameSearch] = useState('')
  const [purlSearch, setPurlSearch] = useState('')
  const [page, setPage] = useState(0)

  useEffect(() => {
    if (!open || !sbom) return
    setLoading(true)
    setComponents(null)
    const t = setTimeout(() => {
      getSbomComponents(sbom.id, { name: nameSearch || undefined, purl: purlSearch || undefined, page, size: 25, sort: 'name,asc' })
        .then(setComponents)
        .catch(() => {})
        .finally(() => setLoading(false))
    }, 200)
    return () => clearTimeout(t)
  }, [sbom, open, nameSearch, purlSearch, page])

  if (!sbom) return null

  return (
    <Modal open={open} onClose={onClose} title={`Components — ${sbom.applicationName}`} maxWidth="max-w-5xl">
      {/* SBOM metadata strip */}
      <div className="flex flex-wrap gap-4 text-xs text-sentinel-400 mb-4 p-3 bg-sentinel-850 rounded-lg">
        <span>Format: <span className="text-sentinel-200">{sbom.format}</span></span>
        <span>Version: <span className="text-sentinel-200">{sbom.specVersion || '—'}</span></span>
        <span>Components: <span className="text-accent-400 font-medium">{sbom.componentCount}</span></span>
        {sbom.artifactDigest && (
          <span className="font-mono">Digest: <span className="text-sentinel-200">{sbom.artifactDigest.substring(0, 16)}…</span></span>
        )}
      </div>

      {/* Search */}
      <div className="flex gap-2 mb-3">
        <input
          type="text"
          placeholder="Search by component name…"
          value={nameSearch}
          onChange={e => { setNameSearch(e.target.value); setPage(0) }}
          className="input-field flex-1 text-sm"
        />
        <input
          type="text"
          placeholder="Search by PURL…"
          value={purlSearch}
          onChange={e => { setPurlSearch(e.target.value); setPage(0) }}
          className="input-field flex-1 text-sm"
        />
      </div>

      {/* Table */}
      {loading ? (
        <div className="flex items-center justify-center py-10">
          <div className="w-6 h-6 border-2 border-accent-500 border-t-transparent rounded-full animate-spin" />
        </div>
      ) : !components || components.content.length === 0 ? (
        <p className="text-sm text-sentinel-500 text-center py-8">
          {nameSearch || purlSearch ? 'No components match your search.' : 'No components in this SBOM.'}
        </p>
      ) : (
        <>
          <div className="overflow-x-auto rounded border border-sentinel-800">
            <table className="w-full text-sm">
              <thead className="bg-sentinel-850 border-b border-sentinel-800">
                <tr className="text-left text-sentinel-400">
                  <th className="px-3 py-2 font-medium">Name</th>
                  <th className="px-3 py-2 font-medium">Version</th>
                  <th className="px-3 py-2 font-medium">Type</th>
                  <th className="px-3 py-2 font-medium">PURL</th>
                </tr>
              </thead>
              <tbody>
                {components.content.map(c => (
                  <tr key={c.id} className="border-b border-sentinel-800/50 hover:bg-sentinel-850/50">
                    <td className="px-3 py-2 text-sentinel-200 font-medium">{c.name}</td>
                    <td className="px-3 py-2 text-sentinel-400 font-mono text-xs">{c.version || '—'}</td>
                    <td className="px-3 py-2 text-sentinel-500 text-xs">{c.componentType || '—'}</td>
                    <td className="px-3 py-2 text-sentinel-400 font-mono text-xs break-all">{c.purl || '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination
            page={components.number}
            size={components.size}
            totalElements={components.totalElements}
            totalPages={components.totalPages}
            onPageChange={setPage}
          />
        </>
      )}
    </Modal>
  )
}

// ── Main page ────────────────────────────────────────────────

export default function Sbom() {
  const navigate = useNavigate()
  const [data, setData] = useState<Page<SbomDocument> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [page, setPage] = useState(0)
  const [selectedSbom, setSelectedSbom] = useState<SbomDocument | null>(null)

  const fetchData = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const result = await listSboms({ page, size: 20, sort: 'createdAt,desc' })
      setData(result)
    } catch (err) {
      setError(extractApiError(err as Parameters<typeof extractApiError>[0]).message)
    } finally {
      setLoading(false)
    }
  }, [page])

  useEffect(() => { fetchData() }, [fetchData])

  // Compact digest display
  const shortDigest = (d: string | null) =>
    d ? d.substring(0, 12) + '…' : '—'

  return (
    <div>
      <PageHeader
        title="SBOM Management"
        subtitle="Software Bill of Materials — component inventory from Syft-generated CycloneDX reports"
        actions={
          <button onClick={fetchData} className="btn-secondary text-sm">
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M16.023 9.348h4.992v-.001M2.985 19.644v-4.992m0 0h4.992m-4.993 0l3.181 3.183a8.25 8.25 0 0011.667 0l3.181-3.183m-4.991-2.698v-4.992m0 0h-4.992m4.992 0l-3.181-3.183a8.25 8.25 0 00-11.667 0l-3.18 3.183" />
            </svg>
            <span className="hidden sm:inline">Refresh</span>
          </button>
        }
      />

      {/* Summary stats */}
      {data && (
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-3 mb-4">
          <div className="card p-4">
            <span className="text-xs text-sentinel-400">SBOM Documents</span>
            <p className="text-2xl font-semibold text-sentinel-100 mt-1">{data.totalElements}</p>
          </div>
        </div>
      )}

      {error && (
        <div className="card p-4 mb-4 bg-red-500/10 border-red-500/30">
          <p className="text-sm text-red-400">{error}</p>
        </div>
      )}

      <div className="card overflow-hidden">
        {loading ? (
          <div className="flex items-center justify-center py-16">
            <div className="w-8 h-8 border-2 border-accent-500 border-t-transparent rounded-full animate-spin" />
          </div>
        ) : !data || data.content.length === 0 ? (
          <div className="px-4 py-16 text-center text-sentinel-500">
            No SBOM documents yet. Generate a CycloneDX report with Syft and submit it via the ingestion API.
          </div>
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-sentinel-850 border-b border-sentinel-800">
                  <tr className="text-left text-sentinel-400">
                    <th className="px-4 py-3 font-medium">Application</th>
                    <th className="px-4 py-3 font-medium">Version</th>
                    <th className="px-4 py-3 font-medium">Components</th>
                    <th className="px-4 py-3 font-medium">Artifact Digest</th>
                    <th className="px-4 py-3 font-medium">Generated</th>
                    <th className="px-4 py-3 font-medium">Ingested</th>
                    <th className="px-4 py-3 font-medium text-right">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {data.content.map(sbom => (
                    <tr key={sbom.id} className="border-b border-sentinel-800/50 hover:bg-sentinel-850/50 transition-colors">
                      <td className="px-4 py-3">
                        <button
                          onClick={() => navigate(`/applications/${sbom.applicationId}`)}
                          className="text-sentinel-100 hover:text-accent-400 font-medium transition-colors"
                        >
                          {sbom.applicationName}
                        </button>
                      </td>
                      <td className="px-4 py-3">
                        <span className="badge badge-info">{sbom.specVersion || '—'}</span>
                      </td>
                      <td className="px-4 py-3 text-accent-400 font-semibold">{sbom.componentCount}</td>
                      <td className="px-4 py-3 text-sentinel-400 font-mono text-xs">{shortDigest(sbom.artifactDigest)}</td>
                      <td className="px-4 py-3 text-sentinel-400 text-xs">
                        {sbom.generatedAt ? new Date(sbom.generatedAt).toLocaleString() : '—'}
                      </td>
                      <td className="px-4 py-3 text-sentinel-400 text-xs">
                        {new Date(sbom.ingestedAt).toLocaleString()}
                      </td>
                      <td className="px-4 py-3 text-right">
                        <button
                          onClick={() => setSelectedSbom(sbom)}
                          className="text-xs btn-secondary py-1 px-2"
                        >
                          Components
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination
              page={data.number}
              size={data.size}
              totalElements={data.totalElements}
              totalPages={data.totalPages}
              onPageChange={setPage}
            />
          </>
        )}
      </div>

      <ComponentModal
        sbom={selectedSbom}
        open={selectedSbom !== null}
        onClose={() => setSelectedSbom(null)}
      />
    </div>
  )
}
