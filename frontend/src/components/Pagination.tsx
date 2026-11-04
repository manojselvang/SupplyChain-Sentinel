interface PaginationProps {
  page: number      // current page (0-based)
  size: number       // page size
  totalElements: number
  totalPages: number
  onPageChange: (page: number) => void
}

export default function Pagination({
  page,
  size,
  totalElements,
  totalPages,
  onPageChange,
}: PaginationProps) {
  if (totalElements === 0) return null

  const firstItem = page * size + 1
  const lastItem = Math.min((page + 1) * size, totalElements)

  return (
    <div className="flex flex-col sm:flex-row items-center justify-between gap-3 px-4 py-3 border-t border-sentinel-800">
      <p className="text-xs text-sentinel-400">
        Showing {firstItem}–{lastItem} of {totalElements}
      </p>

      <div className="flex items-center gap-1">
        <button
          onClick={() => onPageChange(page - 1)}
          disabled={page === 0}
          className="px-3 py-1.5 text-sm rounded-lg border border-sentinel-700 text-sentinel-300 hover:bg-sentinel-800 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
        >
          Previous
        </button>

        <span className="px-3 py-1.5 text-sm text-sentinel-400">
          Page {page + 1} of {totalPages}
        </span>

        <button
          onClick={() => onPageChange(page + 1)}
          disabled={page >= totalPages - 1}
          className="px-3 py-1.5 text-sm rounded-lg border border-sentinel-700 text-sentinel-300 hover:bg-sentinel-800 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
        >
          Next
        </button>
      </div>
    </div>
  )
}
