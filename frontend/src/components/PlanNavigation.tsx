import {useRef, useState} from 'react';
import type {PlanResponse} from '../api/tripsApi';

export function PlanNavigation({plans, selectedId, onSelect, disabled}: {
  plans: PlanResponse[]; selectedId: string; onSelect: (id: string) => void; disabled: boolean;
}) {
  const refs = useRef<(HTMLButtonElement | null)[]>([]);
  const [focusId, setFocusId] = useState(selectedId);
  return <nav className="plan-navigation" aria-label="Plan navigation">
    <div role="tablist" aria-label="Trip plans" className="plan-tablist">
      {plans.map((plan, index) => <button key={plan.id} type="button" role="tab"
        id={`plan-tab-${plan.id}`} aria-controls={`plan-panel-${plan.id}`}
        aria-selected={selectedId === plan.id} tabIndex={(plans.some(p => p.id === focusId) ? focusId : selectedId) === plan.id ? 0 : -1}
        ref={el => {refs.current[index] = el;}} disabled={disabled}
        onFocus={() => setFocusId(plan.id)} onClick={() => onSelect(plan.id)}
        onKeyDown={event => {
          let next = index;
          if (event.key === 'ArrowRight') next = (index + 1) % plans.length;
          else if (event.key === 'ArrowLeft') next = (index - 1 + plans.length) % plans.length;
          else if (event.key === 'Home') next = 0;
          else if (event.key === 'End') next = plans.length - 1;
          else return;
          event.preventDefault(); refs.current[next]?.focus();
        }}>
        {plan.name} {plan.primary && <span className="badge badge-upcoming">Primary</span>} {plan.booked && <span className="badge badge-booked">Booked</span>}
      </button>)}
    </div>
    <label className="plan-select">All plans <select value={selectedId} disabled={disabled} onChange={event => onSelect(event.target.value)}>
      {plans.map(plan => <option key={plan.id} value={plan.id}>{plan.name}{plan.primary ? ' (Primary)' : ''}</option>)}
    </select></label>
  </nav>;
}
