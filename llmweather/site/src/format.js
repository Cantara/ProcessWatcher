export const CONDITIONS = {
  SUNNY: { label: 'Sunny', status: 'good' },
  PARTLY_CLOUDY: { label: 'Partly cloudy', status: 'warning' },
  CLOUDY: { label: 'Cloudy', status: 'warning' },
  RAIN: { label: 'Rain', status: 'serious' },
  STORM: { label: 'Storm', status: 'critical' },
  UNKNOWN: { label: 'No report', status: 'none' },
}

export function conditionLabel(c) {
  return (CONDITIONS[c] || CONDITIONS.UNKNOWN).label
}

export function conditionStatus(c) {
  return (CONDITIONS[c] || CONDITIONS.UNKNOWN).status
}

export const ALERT_STATUS = { severe: 'critical', warning: 'serious', advisory: 'warning' }

export const FRONT_LABEL = {
  'price-drop': 'Price cut',
  'price-rise': 'Price increase',
  'new-model': 'New model',
  deprecation: 'Delisted',
}

export function fmtDateTime(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  return d.toLocaleString(undefined, { weekday: 'short', day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })
}

export function fmtTime(iso) {
  if (!iso) return ''
  return new Date(iso).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })
}

export function fmtShort(iso) {
  if (!iso) return ''
  return new Date(iso).toLocaleString(undefined, { weekday: 'short', hour: '2-digit', minute: '2-digit' })
}

export function fmtAgo(iso, now = Date.now()) {
  if (!iso) return ''
  const mins = Math.round((now - new Date(iso).getTime()) / 60000)
  if (mins < 1) return 'just now'
  if (mins < 60) return `${mins} min ago`
  const h = Math.round(mins / 60)
  if (h < 48) return `${h} h ago`
  return `${Math.round(h / 24)} d ago`
}

export function fmtMoney(v) {
  if (v === null || v === undefined) return 'n/a'
  if (v === 0) return 'free'
  if (v < 1) return `$${v.toFixed(3).replace(/0+$/, '').replace(/\.$/, '')}`
  return `$${v.toFixed(2)}`
}

export function fmtContext(tokens) {
  if (!tokens) return 'n/a'
  if (tokens >= 1_000_000) return `${(tokens / 1_000_000).toFixed(tokens % 1_000_000 ? 1 : 0)}M`
  if (tokens >= 1_000) return `${Math.round(tokens / 1_000)}K`
  return String(tokens)
}

export function fmtLatency(ms) {
  if (ms === null || ms === undefined) return 'n/a'
  if (ms >= 1000) return `${(ms / 1000).toFixed(1)} s`
  return `${ms} ms`
}

export function fmtReleased(epochSeconds) {
  if (!epochSeconds) return ''
  return new Date(epochSeconds * 1000).toLocaleDateString(undefined, { year: 'numeric', month: 'short' })
}
