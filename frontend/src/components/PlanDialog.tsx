import {useEffect, useRef, type ReactNode} from 'react';

export function PlanDialog({title, children, onClose, pending}: {title: string; children: ReactNode; onClose: () => void; pending: boolean}) {
  const ref = useRef<HTMLDivElement>(null);
  const close = useRef(onClose); close.current = onClose;
  const busy = useRef(pending); busy.current = pending;
  useEffect(() => {
    const previous = document.activeElement as HTMLElement;
    ref.current?.querySelector<HTMLElement>('input, select, button')?.focus();
    return () => {
      if (previous?.isConnected) previous.focus();
      else document.querySelector<HTMLElement>('[role="tab"][aria-selected="true"], #workspace-heading')?.focus();
    };
  }, []);
  return <div className="modal-backdrop modal-overlay"><div className="modal modal-content" role="dialog" aria-modal="true" aria-labelledby="plan-dialog-title" ref={ref}
    onKeyDown={event => {
      if (event.key === 'Escape' && !busy.current) {event.preventDefault(); close.current();}
      if (event.key !== 'Tab') return;
      const items = Array.from(ref.current?.querySelectorAll<HTMLElement>('input:not(:disabled),select:not(:disabled),button:not(:disabled)') ?? []);
      const first = items[0], last = items.at(-1);
      if (event.shiftKey && document.activeElement === first) {event.preventDefault(); last?.focus();}
      else if (!event.shiftKey && document.activeElement === last) {event.preventDefault(); first?.focus();}
    }}><h2 id="plan-dialog-title">{title}</h2>{children}</div></div>;
}
