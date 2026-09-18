import {useState} from 'react';

export function AboutDemoTab() {
  const [open, setOpen] = useState(false);
  return <aside className={`about-tab ${open ? 'open' : ''}`} aria-label="About this demo">
    <button type="button" aria-expanded={open} aria-controls="about-demo-content" onClick={() => setOpen((current) => !current)}>About this demo</button>
    <section id="about-demo-content" hidden={!open}>
      <h2>About this demo</h2>
      <p>Suppliers, schedules, prices, availability, and bookings are fictional. No payment or real reservation occurs.</p>
      <p>Travel begins at PDX and destinations are San Francisco, Munich, and Mexico City. Travel dates are limited to March 2027.</p>
      <p>Fictional itineraries can be created, expanded, compared, booked, and canceled.</p>
    </section>
  </aside>;
}
