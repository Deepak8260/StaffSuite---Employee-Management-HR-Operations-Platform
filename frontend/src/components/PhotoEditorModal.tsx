import { RotateCcw, RotateCw, X } from 'lucide-react'
import { useCallback, useEffect, useRef, useState, type PointerEvent as ReactPointerEvent } from 'react'

interface Props {
  file: File
  busy?: boolean
  onCancel: () => void
  /** Receives the finished square picture (JPEG) with the crop, zoom, rotation and light adjustments baked in. */
  onApply: (edited: File) => void
}

const PREVIEW = 320
const OUTPUT = 512

interface Adjust { zoom: number; brightness: number; contrast: number; saturation: number; rotation: number }
const NEUTRAL: Adjust = { zoom: 1, brightness: 100, contrast: 100, saturation: 100, rotation: 0 }

/** Draws the picture, cropped to a square, onto a canvas of the given size. Used for both the preview and the export. */
function paint(ctx: CanvasRenderingContext2D, img: HTMLImageElement, size: number, a: Adjust, offset: { x: number; y: number }) {
  ctx.save()
  ctx.fillStyle = '#ffffff'
  ctx.fillRect(0, 0, size, size)
  const turned = a.rotation % 180 !== 0
  const w = turned ? img.naturalHeight : img.naturalWidth
  const h = turned ? img.naturalWidth : img.naturalHeight
  const scale = (size / Math.min(w, h)) * a.zoom
  ctx.filter = `brightness(${a.brightness}%) contrast(${a.contrast}%) saturate(${a.saturation}%)`
  ctx.translate(size / 2 + offset.x * (size / PREVIEW), size / 2 + offset.y * (size / PREVIEW))
  ctx.rotate((a.rotation * Math.PI) / 180)
  ctx.scale(scale, scale)
  ctx.drawImage(img, -img.naturalWidth / 2, -img.naturalHeight / 2)
  ctx.restore()
}

/** How far the picture may be dragged before an edge would show inside the crop. */
function limit(img: HTMLImageElement, a: Adjust): { x: number; y: number } {
  const turned = a.rotation % 180 !== 0
  const w = turned ? img.naturalHeight : img.naturalWidth
  const h = turned ? img.naturalWidth : img.naturalHeight
  const scale = (PREVIEW / Math.min(w, h)) * a.zoom
  return { x: Math.max(0, (w * scale - PREVIEW) / 2), y: Math.max(0, (h * scale - PREVIEW) / 2) }
}

/** Crop (drag), resize (zoom), rotate and adjust light before a picture becomes the profile photo. */
export default function PhotoEditorModal({ file, busy = false, onCancel, onApply }: Props) {
  const canvas = useRef<HTMLCanvasElement>(null)
  const drag = useRef<{ x: number; y: number; ox: number; oy: number } | null>(null)
  const [img, setImg] = useState<HTMLImageElement | null>(null)
  const [failed, setFailed] = useState(false)
  const [adjust, setAdjust] = useState<Adjust>(NEUTRAL)
  const [offset, setOffset] = useState({ x: 0, y: 0 })

  useEffect(() => {
    let current = true
    const url = URL.createObjectURL(file)
    const image = new Image()
    image.onload = () => { if (current) { setFailed(false); setImg(image) } }
    image.onerror = () => { if (current) setFailed(true) }
    image.src = url
    return () => {
      current = false
      URL.revokeObjectURL(url)
    }
  }, [file])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape' && !busy) onCancel() }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [busy, onCancel])

  const clamp = useCallback((o: { x: number; y: number }, a: Adjust, image: HTMLImageElement) => {
    const l = limit(image, a)
    return { x: Math.min(l.x, Math.max(-l.x, o.x)), y: Math.min(l.y, Math.max(-l.y, o.y)) }
  }, [])

  useEffect(() => {
    const ctx = canvas.current?.getContext('2d')
    if (img && ctx) paint(ctx, img, PREVIEW, adjust, offset)
  }, [img, adjust, offset])

  function change(patch: Partial<Adjust>) {
    if (!img) return
    const next = { ...adjust, ...patch }
    setAdjust(next)
    setOffset((o) => clamp(o, next, img))   // zooming out or turning must not leave an empty edge
  }

  function down(e: ReactPointerEvent) {
    drag.current = { x: e.clientX, y: e.clientY, ox: offset.x, oy: offset.y }
    e.currentTarget.setPointerCapture(e.pointerId)
  }
  function move(e: ReactPointerEvent) {
    const d = drag.current
    if (!d || !img) return
    setOffset(clamp({ x: d.ox + e.clientX - d.x, y: d.oy + e.clientY - d.y }, adjust, img))
  }
  function up() { drag.current = null }

  function apply() {
    if (!img) return
    const out = document.createElement('canvas')
    out.width = OUTPUT
    out.height = OUTPUT
    const ctx = out.getContext('2d')
    if (!ctx) return
    paint(ctx, img, OUTPUT, adjust, offset)
    out.toBlob((blob) => {
      if (blob) onApply(new File([blob], 'profile-photo.jpg', { type: 'image/jpeg' }))
    }, 'image/jpeg', 0.9)
  }

  const slider = (label: string, key: 'zoom' | 'brightness' | 'contrast' | 'saturation', min: number, max: number, step: number, shown: string) => (
    <label className="editor-slider">
      <span>{label}<b>{shown}</b></span>
      <input type="range" min={min} max={max} step={step} value={adjust[key]} disabled={!img || busy}
        onChange={(e) => change({ [key]: Number(e.target.value) })} />
    </label>
  )

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-label="Edit photo">
      <div className="modal photo-editor">
        <button type="button" className="modal-x" aria-label="Cancel" onClick={onCancel} disabled={busy}><X size={20} aria-hidden="true" /></button>
        <h2>Edit your photo</h2>
        <p className="hint">Drag to reposition. The circle shows what will be used.</p>

        <div className="editor-stage" style={{ width: PREVIEW, height: PREVIEW }}>
          {failed ? <p className="error">This image could not be opened.</p> : (
            <>
              <canvas ref={canvas} width={PREVIEW} height={PREVIEW} onPointerDown={down} onPointerMove={move} onPointerUp={up} onPointerCancel={up}
                aria-label="Photo preview, drag to reposition" />
              <span className="editor-ring" aria-hidden="true" />
            </>
          )}
        </div>

        <div className="editor-controls">
          {slider('Zoom', 'zoom', 1, 4, 0.01, `${Math.round(adjust.zoom * 100)}%`)}
          {slider('Brightness', 'brightness', 50, 150, 1, `${adjust.brightness}%`)}
          {slider('Contrast', 'contrast', 50, 150, 1, `${adjust.contrast}%`)}
          {slider('Colour', 'saturation', 0, 200, 1, `${adjust.saturation}%`)}
          <div className="editor-row">
            <button type="button" className="secondary" disabled={!img || busy} onClick={() => change({ rotation: (adjust.rotation + 270) % 360 })}><RotateCcw size={16} aria-hidden="true" /> Rotate left</button>
            <button type="button" className="secondary" disabled={!img || busy} onClick={() => change({ rotation: (adjust.rotation + 90) % 360 })}><RotateCw size={16} aria-hidden="true" /> Rotate right</button>
            <button type="button" className="secondary" disabled={!img || busy} onClick={() => { setAdjust(NEUTRAL); setOffset({ x: 0, y: 0 }) }}>Reset</button>
          </div>
        </div>

        <div className="actions">
          <button type="button" className="btn-primary" onClick={apply} disabled={!img || busy}>{busy ? 'Saving…' : 'Use this photo'}</button>
          <button type="button" className="secondary" onClick={onCancel} disabled={busy}>Cancel</button>
        </div>
      </div>
    </div>
  )
}
