import { useState } from 'react'
import { useAuth } from '../context/AuthContext'

export default function TopBar({ onMenuClick }: { onMenuClick: () => void }) {
  const { user, logout } = useAuth()
  const [menuOpen, setMenuOpen] = useState(false)

  return (
    <header className="h-16 bg-sentinel-900 border-b border-sentinel-800 flex items-center justify-between px-4 lg:px-6 shrink-0">
      <div className="flex items-center gap-3">
        {/* Mobile menu toggle */}
        <button
          onClick={onMenuClick}
          className="lg:hidden text-sentinel-300 hover:text-sentinel-100 p-1"
          aria-label="Toggle sidebar"
        >
          <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M3.75 6.75h16.5M3.75 12h16.5m-16.5 5.25h16.5" />
          </svg>
        </button>

        {/* Page title can be injected later via context; static for now */}
        <div className="hidden sm:flex items-center gap-2 text-sentinel-400 text-sm">
          <span className="text-sentinel-300">Supply Chain Security</span>
          <span>›</span>
          <span>Risk &amp; Release Assurance</span>
        </div>
      </div>

      {/* Right section */}
      <div className="flex items-center gap-4">
        {/* Health indicator */}
        <div className="hidden sm:flex items-center gap-2 text-xs text-sentinel-400">
          <span className="w-2 h-2 rounded-full bg-green-500 animate-pulse" />
          Backend Connected
        </div>

        {/* User menu */}
        <div className="relative">
          <button
            onClick={() => setMenuOpen(!menuOpen)}
            className="flex items-center gap-2 text-sm text-sentinel-200 hover:text-sentinel-100 p-1 rounded-lg hover:bg-sentinel-800 transition-colors"
          >
            <div className="w-8 h-8 rounded-full bg-accent-500/20 flex items-center justify-center text-accent-400 font-medium text-sm">
              {user?.displayName?.[0]?.toUpperCase() ?? 'G'}
            </div>
            <span className="hidden sm:inline">{user?.displayName ?? 'Guest'}</span>
            <svg className="w-4 h-4 text-sentinel-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M19.5 8.25l-7.5 7.5-7.5-7.5" />
            </svg>
          </button>

          {menuOpen && (
            <div className="absolute right-0 mt-2 w-48 card shadow-lg py-1 z-50">
              <div className="px-3 py-2 border-b border-sentinel-800">
                <p className="text-sm text-sentinel-100 font-medium">{user?.displayName ?? 'Guest User'}</p>
                <p className="text-xs text-sentinel-400">{user?.email ?? 'Not authenticated'}</p>
              </div>
              <button
                onClick={() => {
                  setMenuOpen(false)
                  logout()
                }}
                className="w-full text-left px-3 py-2 text-sm text-sentinel-300 hover:bg-sentinel-800 hover:text-sentinel-100 transition-colors"
              >
                Sign Out
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  )
}
