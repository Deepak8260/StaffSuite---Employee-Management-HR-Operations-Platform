import type { PageResponse } from '../types/auth'

interface Props {
  data: PageResponse<unknown>
  onPage: (page: number) => void
  noun?: string
}

export default function Pager({ data, onPage, noun = 'results' }: Props) {
  return (
    <div className="pager">
      <button disabled={data.page === 0} onClick={() => onPage(data.page - 1)}>
        Previous
      </button>
      <span>
        Page {data.page + 1} of {Math.max(data.totalPages, 1)} ({data.totalElements} {noun})
      </span>
      <button disabled={data.page + 1 >= data.totalPages} onClick={() => onPage(data.page + 1)}>
        Next
      </button>
    </div>
  )
}
