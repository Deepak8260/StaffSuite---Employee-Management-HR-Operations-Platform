import { BULK_UPLOAD_GUIDE } from './bulkUploadGuide'

interface Line { column: string; need: 'required' | 'optional'; detail: string }

// the card is built from the very same text the backend returns on a header mismatch, so the two cannot drift apart
const paragraphs = BULK_UPLOAD_GUIDE.split('\n\n')
const columnLines: Line[] = paragraphs[1].split('\n').flatMap((line) => {
  const m = /^(\S+)\s+\((required|optional), (.*)\)$/.exec(line.trim())
  return m ? [{ column: m[1], need: m[2] as Line['need'], detail: m[3] }] : []
})

/** The Excel column guide laid out as a table: column name, whether it is needed, and what it must contain. */
export default function BulkUploadGuideCard() {
  return (
    <div className="guide-card">
      <p>{paragraphs[0]}</p>
      <table>
        <thead>
          <tr><th>Column</th><th>Needed</th><th>Details</th></tr>
        </thead>
        <tbody>
          {columnLines.map((l) => (
            <tr key={l.column}>
              <td><code>{l.column}</code></td>
              <td><span className={`guide-need ${l.need}`}>{l.need === 'required' ? 'Required' : 'Optional'}</span></td>
              <td>{l.detail}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <p>{paragraphs[2]}</p>
    </div>
  )
}
