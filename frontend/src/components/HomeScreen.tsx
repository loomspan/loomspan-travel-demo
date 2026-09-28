import {ActionIcon} from './ActionIcon';
import {FeaturedDestinations} from './FeaturedDestinations';

export type StartMode = 'PLAN_TRIP' | 'AIRFARE' | 'STAY';

export function HomeScreen({onStart, onReturn}: {onStart: (mode: StartMode) => void; onReturn?: {label: string; open: () => void}}) {
  return <section className="card profile-card home-card" aria-labelledby="home-heading">
    <div className="home-hero">
      <p className="eyebrow wordmark">DeTour</p>
      <h1 id="home-heading" tabIndex={-1}>Home</h1>
      <p className="home-lead">A little farther feels closer from here.</p>
      <p>Build a full trip or begin with the part you know. Your next journey starts in one place.</p>
      {onReturn && <button type="button" className="primary" onClick={onReturn.open}>Return to {onReturn.label}</button>}
    </div>
    <section className="home-start" aria-labelledby="home-start-heading">
      <div className="home-start-heading"><p className="eyebrow">MAKE IT YOURS</p><h2 id="home-start-heading">Start planning</h2></div>
      <div className="home-action-grid">
        <article className="home-action-card"><ActionIcon name="trip" /><h3>Plan the whole trip</h3><p>Bring your travel details together and compare the possibilities.</p><button type="button" className="secondary" onClick={() => onStart('PLAN_TRIP')}>Plan Trip</button></article>
        <article className="home-action-card"><ActionIcon name="airfare" /><h3>Find your flight</h3><p>Start with airfare and build the rest when you are ready.</p><button type="button" className="secondary" onClick={() => onStart('AIRFARE')}>Airfare</button></article>
        <article className="home-action-card"><ActionIcon name="stay" /><h3>Choose your stay</h3><p>Begin with a place to land, then make it a journey.</p><button type="button" className="secondary" onClick={() => onStart('STAY')}>Stay</button></article>
      </div>
    </section>
    <FeaturedDestinations />
  </section>;
}
