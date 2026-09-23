import {useEffect, useRef, useState} from 'react';

export function AboutDemoTab() {
  const [open, setOpen] = useState(false);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const headingRef = useRef<HTMLHeadingElement>(null);

  const close = () => {
    setOpen(false);
    triggerRef.current?.focus();
  };

  useEffect(() => {
    if (!open) return;
    headingRef.current?.focus();
  }, [open]);

  return <aside className={`about-tab ${open ? 'open' : ''}`} aria-label="About this demo" onKeyDown={(event) => {
    if (open && event.key === 'Escape') { event.preventDefault(); close(); }
  }}>
    <button ref={triggerRef} type="button" aria-expanded={open} aria-controls="about-demo-content" onClick={() => open ? close() : setOpen(true)}>About this demo</button>
    <section id="about-demo-content" aria-labelledby="about-demo-heading" hidden={!open}>
      <h2 id="about-demo-heading" ref={headingRef} tabIndex={-1}>About this demo</h2>
      <p>Suppliers, schedules, prices, availability, and bookings are fictional. No payment or real reservation occurs.</p>
      <p>Trips start at PDX, travel to San Francisco, Munich, or Mexico City, and use dates in March 2027.</p>
      <p>Start with Plan Trip, Airfare, or Stay. Add other components explicitly, save a Draft as Planned, compare up to three Planned alternatives, then review one to book.</p>
      <p>You can cancel an active Booking only before its departure date begins in the PDX (America/Los_Angeles) timezone.</p>
      <button type="button" className="about-tab-close" onClick={close}>Close About this demo</button>
    </section>
  </aside>;
}
