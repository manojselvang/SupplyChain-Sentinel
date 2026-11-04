import { useState, useEffect, type FormEvent } from 'react'
import Modal from '../components/Modal'
import {
  CRITICALITY_LEVELS,
  LIFECYCLE_STATUSES,
  DATA_CLASSIFICATIONS,
  HOSTING_ENVIRONMENTS,
  type Application,
  type CreateApplicationRequest,
  type UpdateApplicationRequest,
} from '../types'
import { createApplication, updateApplication } from '../api/applications'
import { extractApiError } from '../api/client'

interface ApplicationFormModalProps {
  open: boolean
  onClose: () => void
  onSaved: () => void
  app?: Application | null  // null/undefined = create mode
}

export default function ApplicationFormModal({ open, onClose, onSaved, app }: ApplicationFormModalProps) {
  const isEdit = !!app
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Form state
  const [applicationKey, setApplicationKey] = useState('')
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [businessUnit, setBusinessUnit] = useState('')
  const [businessOwner, setBusinessOwner] = useState('')
  const [technicalOwner, setTechnicalOwner] = useState('')
  const [criticality, setCriticality] = useState('MEDIUM')
  const [lifecycleStatus, setLifecycleStatus] = useState('ACTIVE')
  const [dataClassification, setDataClassification] = useState('INTERNAL')
  const [repositoryUrl, setRepositoryUrl] = useState('')
  const [primaryLanguage, setPrimaryLanguage] = useState('')
  const [hostingEnvironment, setHostingEnvironment] = useState('ON_PREMISES')
  const [internetFacing, setInternetFacing] = useState(false)

  // Pre-populate when editing
  useEffect(() => {
    if (app) {
      setApplicationKey(app.applicationKey)
      setName(app.name)
      setDescription(app.description ?? '')
      setBusinessUnit(app.businessUnit ?? '')
      setBusinessOwner(app.businessOwner ?? '')
      setTechnicalOwner(app.technicalOwner ?? '')
      setCriticality(app.criticality)
      setLifecycleStatus(app.lifecycleStatus)
      setDataClassification(app.dataClassification)
      setRepositoryUrl(app.repositoryUrl ?? '')
      setPrimaryLanguage(app.primaryLanguage ?? '')
      setHostingEnvironment(app.hostingEnvironment)
      setInternetFacing(app.internetFacing)
    } else {
      // Reset for create mode
      setApplicationKey('')
      setName('')
      setDescription('')
      setBusinessUnit('')
      setBusinessOwner('')
      setTechnicalOwner('')
      setCriticality('MEDIUM')
      setLifecycleStatus('ACTIVE')
      setDataClassification('INTERNAL')
      setRepositoryUrl('')
      setPrimaryLanguage('')
      setHostingEnvironment('ON_PREMISES')
      setInternetFacing(false)
    }
    setError(null)
  }, [app, open])

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setLoading(true)

    try {
      if (isEdit) {
        const payload: UpdateApplicationRequest = {
          name: name || undefined,
          description: description || undefined,
          businessUnit: businessUnit || undefined,
          businessOwner: businessOwner || undefined,
          technicalOwner: technicalOwner || undefined,
          criticality,
          lifecycleStatus,
          dataClassification,
          repositoryUrl: repositoryUrl || undefined,
          primaryLanguage: primaryLanguage || undefined,
          hostingEnvironment,
          internetFacing,
        }
        await updateApplication(app!.id, payload)
      } else {
        const payload: CreateApplicationRequest = {
          applicationKey,
          name,
          description: description || undefined,
          businessUnit: businessUnit || undefined,
          businessOwner: businessOwner || undefined,
          technicalOwner: technicalOwner || undefined,
          criticality,
          lifecycleStatus,
          dataClassification,
          repositoryUrl: repositoryUrl || undefined,
          primaryLanguage: primaryLanguage || undefined,
          hostingEnvironment,
          internetFacing,
        }
        await createApplication(payload)
      }
      onSaved()
      onClose()
    } catch (err) {
      setError(extractApiError(err as Parameters<typeof extractApiError>[0]).message)
    } finally {
      setLoading(false)
    }
  }

  const label = 'block text-sm text-sentinel-300 mb-1.5'
  const field = 'input-field'

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={isEdit ? 'Edit Application' : 'Register Application'}
      maxWidth="max-w-3xl"
    >
      <form onSubmit={handleSubmit} className="space-y-4">
        {error && (
          <div className="bg-red-500/10 border border-red-500/30 rounded-lg px-3 py-2">
            <p className="text-sm text-red-400">{error}</p>
          </div>
        )}

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <label className={label}>Application Key *</label>
            <input
              type="text"
              value={applicationKey}
              onChange={(e) => setApplicationKey(e.target.value)}
              className={field}
              placeholder="e.g. payment-gateway"
              maxLength={100}
              disabled={isEdit}
              required={!isEdit}
            />
            {isEdit && <p className="text-xs text-sentinel-500 mt-1">Cannot be changed after creation</p>}
          </div>

          <div>
            <label className={label}>Name *</label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              className={field}
              placeholder="e.g. Payment Gateway"
              maxLength={255}
              required
            />
          </div>
        </div>

        <div>
          <label className={label}>Description</label>
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            className={`${field} min-h-[80px] resize-y`}
            placeholder="Brief description of the application"
          />
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <label className={label}>Business Unit</label>
            <input type="text" value={businessUnit} onChange={(e) => setBusinessUnit(e.target.value)} className={field} maxLength={255} />
          </div>
          <div>
            <label className={label}>Business Owner</label>
            <input type="text" value={businessOwner} onChange={(e) => setBusinessOwner(e.target.value)} className={field} maxLength={255} />
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <label className={label}>Technical Owner</label>
            <input type="text" value={technicalOwner} onChange={(e) => setTechnicalOwner(e.target.value)} className={field} maxLength={255} />
          </div>
          <div>
            <label className={label}>Repository URL</label>
            <input type="text" value={repositoryUrl} onChange={(e) => setRepositoryUrl(e.target.value)} className={field} placeholder="https://github.com/..." maxLength={500} />
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <label className={label}>Primary Language</label>
            <input type="text" value={primaryLanguage} onChange={(e) => setPrimaryLanguage(e.target.value)} className={field} placeholder="Java, Python, ..." maxLength={100} />
          </div>
          <div>
            <label className={label}>Hosting Environment *</label>
            <select value={hostingEnvironment} onChange={(e) => setHostingEnvironment(e.target.value)} className={field}>
              {HOSTING_ENVIRONMENTS.map((env) => (
                <option key={env} value={env}>{env.replace('_', ' ')}</option>
              ))}
            </select>
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <div>
            <label className={label}>Criticality *</label>
            <select value={criticality} onChange={(e) => setCriticality(e.target.value)} className={field}>
              {CRITICALITY_LEVELS.map((c) => (
                <option key={c} value={c}>{c}</option>
              ))}
            </select>
          </div>
          <div>
            <label className={label}>Lifecycle Status *</label>
            <select value={lifecycleStatus} onChange={(e) => setLifecycleStatus(e.target.value)} className={field}>
              {LIFECYCLE_STATUSES.map((s) => (
                <option key={s} value={s}>{s}</option>
              ))}
            </select>
          </div>
          <div>
            <label className={label}>Data Classification *</label>
            <select value={dataClassification} onChange={(e) => setDataClassification(e.target.value)} className={field}>
              {DATA_CLASSIFICATIONS.map((d) => (
                <option key={d} value={d}>{d}</option>
              ))}
            </select>
          </div>
        </div>

        <div>
          <label className="flex items-center gap-2 cursor-pointer">
            <input
              type="checkbox"
              checked={internetFacing}
              onChange={(e) => setInternetFacing(e.target.checked)}
              className="w-4 h-4 rounded accent-accent-500"
            />
            <span className="text-sm text-sentinel-300">Internet-facing application</span>
          </label>
        </div>

        <div className="flex justify-end gap-2 pt-2 border-t border-sentinel-800">
          <button type="button" onClick={onClose} disabled={loading} className="btn-secondary text-sm">
            Cancel
          </button>
          <button type="submit" disabled={loading} className="btn-primary text-sm">
            {loading ? 'Saving...' : isEdit ? 'Update Application' : 'Register Application'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
