import {useId, type ReactNode} from 'react';
import {formatTallyCents} from './ItinerarySummaryTally';
export type SelectionRow = {category: 'AIRFARE' | 'STAY' | 'RENTAL'; label: string; present: boolean; title: string; details: string; extraDetails?: ReactNode; basis: string; total?: number; locked?: string; remove?: () => void};
export function PlanSelectionsSummary({rows, total, partial, guest, pending, onChange, onSave}: {rows: SelectionRow[]; total?: number; partial: boolean; guest: boolean; pending: boolean; onChange: (c: SelectionRow['category']) => void; onSave?: () => void}) {
  const id = useId();
  return <section className="panel-inset plan-selections-summary" aria-labelledby={`${id}-selections`}><h3 id={`${id}-selections`}>Your selections</h3>
    {guest && <p className="hint">Not yet saved</p>}
    {rows.map(row => <article key={row.category} className="plan-selection-row" aria-label={`${row.label} selection`}><h4>{row.label}{row.category === 'RENTAL' && ' (optional)'}</h4>
      {row.present ? <><p className="guest-selection-label">{row.locked ?? `Selected ${row.label.toLowerCase()}`}</p><strong>{row.title}</strong><p>{formatTallyCents(row.total)}</p><p className="hint">{row.basis}</p><details><summary>{row.label} details</summary><p>{row.details}</p>{row.extraDetails}</details></> : <span className="missing-component">Not selected</span>}
      <div className="button-row"><button type="button" className="text-button button-sm" disabled={pending || Boolean(row.locked)} onClick={() => onChange(row.category)}>{row.present ? 'Change' : 'Find'} {row.label.toLowerCase()}</button>{row.present && row.remove && <button type="button" className="text-button button-sm" disabled={pending || Boolean(row.locked)} onClick={row.remove}>Remove {row.label.toLowerCase()}</button>}</div>
    </article>)}
    <p className="plan-total">{partial ? 'Partial total' : 'Total'}: {formatTallyCents(total)}</p>{partial && <p className="hint">Subtotal of selected components; missing choices are not included.</p>}
    {guest && onSave && rows.some(r => r.present) && <div className="button-row"><button type="button" className="primary" disabled={pending} onClick={onSave}>Save selections</button></div>}
  </section>;
}
