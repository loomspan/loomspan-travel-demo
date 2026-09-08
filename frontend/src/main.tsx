import React,{useEffect,useRef,useState} from 'react';
import {createRoot} from 'react-dom/client';
import type {Assessment,Quote,Trip,TripRequest,TripSummary} from './types';
import './style.css';

async function api<T>(path:string,method='GET',body?:unknown):Promise<T>{
  const response=await fetch('/api'+path,{method,headers:body===undefined?{}:{'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body)});
  if(!response.ok){const error=await response.json().catch(()=>({message:'The server could not complete this request.'}));throw new Error(error.message||`Request failed (${response.status}).`);}
  return response.json();
}
const money=(cents:number)=>new Intl.NumberFormat('en-US',{style:'currency',currency:'USD',maximumFractionDigits:cents%100?2:0}).format(cents/100);
const time=(stamp:string)=>new Intl.DateTimeFormat('en-US',{timeZone:'America/New_York',hour:'2-digit',minute:'2-digit',hour12:false}).format(new Date(stamp));
const mode=(value:string)=>value==='rail'?'Rail':'Flight';
const active=(a:Assessment|undefined)=>a?.status==='QUEUED'||a?.status==='RUNNING';
const scope='Transport for two, one room for two nights, and transfers. Meals, show tickets, and incidental travel are excluded.';

function App(){
  const [example,setExample]=useState<TripRequest|null>(null),[draft,setDraft]=useState<TripRequest|null>(null);
  const [trip,setTrip]=useState<Trip|null>(null),[trips,setTrips]=useState<TripSummary[]>([]);
  const [screen,setScreen]=useState<'request'|'compare'|'booked'>('request');
  const [busy,setBusy]=useState(false),[error,setError]=useState(''),[pollError,setPollError]=useState('');
  const [review,setReview]=useState<Quote|null>(null),[tick,setTick]=useState(Date.now());
  const selectionVersion=useRef(0),bookingKey=useRef(''),reviewButton=useRef<HTMLButtonElement>(null);
  const assessment=trip?.assessments.find(a=>a.revision===trip.revision&&a.catalogVersion===trip.catalogVersion);
  const running=active(assessment);
  const changing=!!trip?.booking;
  const disrupted=!!trip?.disruption;
  const actionable=!trip?.booking || assessment?.baseBookingId===trip.booking.id;
  const proposal=assessment?.status==='SUCCEEDED'?assessment.result:null;
  async function refreshList(){setTrips(await api<TripSummary[]>('/trips'));}
  async function load(id:string){
    const version=++selectionVersion.current;setBusy(true);setError('');setReview(null);
    try{const value=await api<Trip>('/trips/'+id);if(version!==selectionVersion.current)return;setTrip(value);setDraft(value.request);setScreen(value.booking&&!value.assessments.some(a=>a.revision===value.revision&&a.baseBookingId===value.booking?.id&&a.catalogVersion===value.catalogVersion)?'booked':value.assessments.some(a=>a.revision===value.revision)?'compare':'request');localStorage.setItem('wayfarer.trip',id);}
    catch(e){if(version===selectionVersion.current)setError((e as Error).message);}
    finally{if(version===selectionVersion.current)setBusy(false);}
  }
  useEffect(()=>{let cancelled=false;Promise.all([api<TripRequest>('/example'),api<TripSummary[]>('/trips')]).then(([sample,list])=>{
    if(cancelled)return;setExample(sample);setDraft(sample);setTrips(list);
    const id=localStorage.getItem('wayfarer.trip');if(id&&list.some(t=>t.id===id))void load(id);
  }).catch(e=>{if(!cancelled)setError(e.message);});return()=>{cancelled=true;};},[]);
  useEffect(()=>{
    if(!trip||(!running&&!trip.booking))return;
    let cancelled=false;const id=trip.id,version=selectionVersion.current;
    const poll=async()=>{try{const value=await api<Trip>('/trips/'+id);if(!cancelled&&version===selectionVersion.current){setTrip(value);setPollError('');}}catch(e){if(!cancelled)setPollError('Connection interrupted. Retrying status check…');}};
    const timer=setInterval(()=>void poll(),running?2500:5000);const clock=setInterval(()=>setTick(Date.now()),1000);
    return()=>{cancelled=true;clearInterval(timer);clearInterval(clock);};
  },[running,trip?.id,!!trip?.booking]);
  useEffect(()=>{if(review)reviewButton.current?.focus();},[review]);
  function newTrip(){selectionVersion.current++;setTrip(null);setDraft(example);setReview(null);setError('');setScreen('request');localStorage.removeItem('wayfarer.trip');}
  async function saveAndAssess(event:React.FormEvent){
    event.preventDefault();if(!draft)return;setBusy(true);setError('');
    try{
      let saved=trip;
      if(!saved)saved=await api<Trip>('/trips','POST',draft);
      else if(saved.booking||JSON.stringify(saved.request)!==JSON.stringify(draft))saved=await api<Trip>('/trips/'+saved.id,'PUT',{revision:saved.revision,request:draft});
      setTrip(saved);setDraft(saved.request);localStorage.setItem('wayfarer.trip',saved.id);setScreen('compare');
      await api<Assessment>('/trips/'+saved.id+'/assessments','POST',{revision:saved.revision});
      setTrip(await api<Trip>('/trips/'+saved.id));await refreshList();setTick(Date.now());
    }catch(e){setError((e as Error).message);}finally{setBusy(false);}
  }
  async function retry(){if(!trip)return;setScreen('compare');setBusy(true);setError('');setReview(null);try{await api('/trips/'+trip.id+'/assessments','POST',{revision:trip.revision});setTrip(await api<Trip>('/trips/'+trip.id));setTick(Date.now());}catch(e){setError((e as Error).message);}finally{setBusy(false);}}
  async function book(){
    if(!trip||!review||!assessment)return;setBusy(true);setError('');
    try{await api('/trips/'+trip.id+(trip.booking?'/exchanges':'/bookings'),'POST',{assessmentId:assessment.id,candidateId:review.candidateId,idempotencyKey:bookingKey.current});setTrip(await api<Trip>('/trips/'+trip.id));setScreen('booked');setReview(null);await refreshList();}
    catch(e){setError((e as Error).message);}finally{setBusy(false);}
  }
  async function cancelReturn(){
    if(!trip?.booking)return;setBusy(true);setError('');setReview(null);
    try {const updated=await api<Trip>('/trips/'+trip.id+'/return-cancellation','POST',{bookingId:trip.booking.id,serviceId:trip.booking.quote.returnServiceId});setTrip(updated);setScreen('booked');await refreshList();}
    catch(e){setError((e as Error).message);}finally{setBusy(false);}
  }
  function choose(q:Quote){bookingKey.current=crypto.randomUUID();setReview(q);setError('');}
  const setField=<K extends keyof TripRequest>(key:K,value:TripRequest[K])=>{if(draft)setDraft({...draft,[key]:value});};
  const edit=()=>{setReview(null);setDraft(trip?.request??example);setScreen('request');setError('');};
  return <div className="app">
    <header><a className="brand" href="/">Wayfarer <span aria-hidden="true">↗</span></a><span className="tagline">Travel thoughtfully. Arrive ready.</span><span className="badge">Simulated travel · Local demo</span></header>
    <div className="shell"><aside><div className="aside-heading">YOUR TRIPS</div><button className="new-trip" onClick={newTrip} disabled={busy||running}>+ Plan a weekend</button>
      {trips.map((item,index)=><button disabled={busy} key={item.id} className={'trip-link '+(trip?.id===item.id?'selected':'')} onClick={()=>void load(item.id)}><span>New York weekend {trips.length-index}</span><small>Oct 16–18 · {item.needsAttention?'Needs attention':item.booked?'Booked':'Planning'}</small></button>)}
      <p className="aside-note">One corridor. Real coordination.<br/>Fictional inventory.</p>
    </aside><main>
      <div className="eyebrow">TWO TRAVELERS · TWO NIGHTS</div><h1>Boston → New York</h1><p className="muted">October 16–18, 2026 · All times Eastern</p>
      <div className="mobile-trips"><label>Saved trips<select value={trip?.id??''} disabled={busy} onChange={e=>e.target.value?void load(e.target.value):newTrip()}><option value="">New weekend</option>{trips.map((t,i)=><option key={t.id} value={t.id}>Weekend {trips.length-i} · {t.needsAttention?'Needs attention':t.booked?'Booked':'Planning'}</option>)}</select></label></div>
      <nav aria-label="Trip steps" className="steps"><button aria-current={screen==='request'?'step':undefined} onClick={edit} disabled={busy||running}>1 · Your request</button><button aria-current={screen==='compare'?'step':undefined} onClick={()=>setScreen('compare')} disabled={!assessment||busy||!actionable}>2 · Compare trips</button><button aria-current={screen==='booked'?'step':undefined} onClick={()=>setScreen('booked')} disabled={!trip?.booking}>3 · Booked trip</button></nav>
      {disrupted&&<section className="notice error" role="alert"><h2>Your return service was canceled</h2><p>{trip!.disruption!.serviceId} is no longer operating. Your outbound journey and hotel remain reserved. The original return times below are no longer a valid itinerary.</p><p>Recovery keeps your outbound service and hotel, and checks your saved budget, modes, and deadlines.</p><button className="primary" disabled={busy||running} onClick={()=>void retry()}>Find a replacement with Loomspan</button></section>}
      {error&&<div className="notice error" role="alert">{error}</div>}
      {!draft&&!error&&<p role="status">Loading the trip workspace…</p>}
      {screen==='request'&&draft&&<form className="panel" onSubmit={saveAndAssess}><div className="row"><h2>{changing?'Change your weekend':'A weekend that fits'}</h2><button type="button" onClick={()=>setDraft(example)} disabled={busy||running}>Load example</button></div>
        <p className="muted">This demo supports the dates below, two adults, and one room. Adjust your budget, timing, and priorities.</p>
        {changing&&<div className="notice"><strong>Keep {trip!.booking!.quote.hotelName}</strong><p>{disrupted?'Your outbound journey and hotel remain reserved. Recovery replaces only the canceled return service.':'Your current booking stays reserved until you accept a replacement. Changes preserve this hotel and room; transport is assessed again against your revised requirements.'}</p><button type="button" disabled={busy||running} onClick={()=>setDraft({...draft,returnToOriginBy:draft.returnDate+'T21:30:00-04:00'})}>Try: back in Boston by 21:30</button></div>}
        <fieldset disabled={busy||running}><div className="fields">
          <label>From<input readOnly value="Boston downtown meeting point"/></label><label>To<input readOnly value="New York"/></label>
          <label>Outbound date<input type="date" readOnly value={draft.outboundDate}/></label><label>Return date<input type="date" readOnly value={draft.returnDate}/></label>
          <label>Maximum trip budget (USD)<input type="number" required min="0.01" max="100000" step="0.01" value={draft.budgetCents/100} onChange={e=>setField('budgetCents',Math.round(Number(e.target.value)*100))}/></label>
          <label>What matters most<select value={draft.priorities[0]} onChange={e=>setField('priorities',e.target.value==='LOWEST_TOTAL'?['LOWEST_TOTAL','QUIET_ROOM','SHORT_TRANSFERS']:['QUIET_ROOM','SHORT_TRANSFERS','LOWEST_TOTAL'])}><option value="QUIET_ROOM">Quiet room, then shorter transfers</option><option value="LOWEST_TOTAL">Lowest complete-trip price</option></select></label>
          <label>Travel modes<select value={draft.allowedModes.length===2?'both':draft.allowedModes[0]} onChange={e=>setField('allowedModes',e.target.value==='both'?['rail','flight']:[e.target.value])}><option value="both">Compare rail and flights</option><option value="rail">Rail only</option><option value="flight">Flights only</option></select></label>
          <label>Hotel-ready by Friday<input required type="time" value={time(draft.hotelReadyBy)} onChange={e=>setField('hotelReadyBy',draft.outboundDate+'T'+e.target.value+':00-04:00')}/></label>
          <label>Leave hotel no earlier than Sunday<input required type="time" min="11:00" value={time(draft.leaveHotelNoEarlierThan)} onChange={e=>setField('leaveHotelNoEarlierThan',draft.returnDate+'T'+e.target.value+':00-04:00')}/></label>
          <label>Back in Boston by Sunday<input required type="time" value={time(draft.returnToOriginBy)} onChange={e=>setField('returnToOriginBy',draft.returnDate+'T'+e.target.value+':00-04:00')}/></label>
        </div><p className="small muted">{scope} The Boston meeting point is the rail station; airport transfers are included when needed.</p><button className="primary" type="submit">{busy?'Saving…':running?'Assessment running…':changing?'Compare booking changes with Loomspan':'Confirm and compare with Loomspan'}</button></fieldset>
      </form>}
      {screen==='compare'&&<>
        {changing&&<div className="notice"><strong>{disrupted?'Outbound journey and hotel remain reserved':'Current booking is still reserved'}</strong><p>{trip!.booking!.quote.hotelName} · {money(trip!.booking!.quote.totalCents)} · Back in Boston at {time(trip!.booking!.quote.returnToOriginAt)}</p><button onClick={()=>{setReview(null);setScreen('booked');}}>{disrupted?'View affected booking':'Keep current booking'}</button></div>}
        <div className="row"><h2>{running?'Finding a trip that fits':proposal?.status==='NO_FEASIBLE_TRIP'?'Your request needs another look':'Your weekend, considered.'}</h2><button onClick={edit} disabled={busy||running}>Edit request</button></div>
        {trip&&<p className="muted">Revision {trip.revision} · {money(trip.request.budgetCents)} maximum · {trip.request.priorities[0]==='QUIET_ROOM'?'Quiet room preferred':'Lowest total preferred'}</p>}
        {running&&<div className="panel" role="status"><div className="pulse"/> <h3>{assessment?.status==='QUEUED'?'Waiting for an assessment slot':'Loomspan is assessing your trip'}</h3><p>Transport, stay, and logistics specialists will compare the complete trip. No additional inventory is reserved during planning.</p><span className="muted">Elapsed {Math.max(0,Math.floor((tick-new Date(assessment!.createdAt).getTime())/1000))} seconds · You can refresh safely.</span>{pollError&&<p>{pollError}</p>}</div>}
        {!assessment&&!busy&&<p>Confirm your request to begin planning.</p>}
        {assessment?.status==='FAILED'&&<div className="notice" role="alert"><h3>Assessment could not finish</h3><p>{assessment.error}</p><button onClick={()=>void retry()} disabled={busy}>Retry assessment</button></div>}
        {proposal&&<>
          {proposal.status==='NO_FEASIBLE_TRIP'?<div className="notice" role="status">{proposal.blockers.map(v=><p key={v.code}>{v.message}</p>)}<p>No bookable proposal was created.</p>{disrupted&&<><h3>Changes to consider</h3>{assessment!.recoverySuggestions.length?assessment!.recoverySuggestions.map((suggestion,i)=><div key={i}><ul>{suggestion.changes.map(change=><li key={change}>{change}</li>)}</ul><button disabled={busy} onClick={()=>{setDraft(suggestion.request);setScreen('request');setReview(null);}}>Review these request changes</button></div>):<p>No replacement is available even with broader modes, budget, and timing within this demo weekend. A preference change cannot restore a canceled or sold-out service.</p>}<p>These are calculated possibilities, not reservations. Review the changes and reassess before accepting any replacement.</p></>}</div>:<div className="options">
            {proposal.recommended&&<TripCard quote={proposal.recommended} label="Recommended" reason={proposal.recommendedReason} budget={trip!.request.budgetCents} primary onChoose={choose} disabled={busy||!actionable} current={trip?.booking?.quote}/>}
            {proposal.alternative&&<TripCard quote={proposal.alternative} label={proposal.recommended&&proposal.alternative.totalCents<proposal.recommended.totalCents?'Save '+money(proposal.recommended.totalCents-proposal.alternative.totalCents):'An alternative'} reason={proposal.alternativeReason} budget={trip!.request.budgetCents} onChoose={choose} disabled={busy||!actionable} current={trip?.booking?.quote}/>}
          </div>}
          <p className="small muted">{scope}</p>
          <details><summary>Why these trips?</summary><p>{proposal.explanation}</p><p className="muted">Java validated {proposal.consideredCount} complete combinations; {proposal.feasibleCount} meet your saved requirements. Inventory is checked again when you book.</p></details>
          <details><summary>How the trip was assessed</summary><p className="small">Loomspan session: <code>{assessment?.sessionId??'Unavailable'}</code></p><p className="small muted">Observed completed-execution events. Expand the same session in Loomspan Console for plans and overlap details.</p><ul className="events">{assessment?.events.map((event,i)=><li key={i}><time>{new Date(event.timestamp).toLocaleTimeString()}</time> {event.type==='SKILL_STARTED'?'Started':'Finished'} · {event.route??event.frameId??'Skill'}</li>)}</ul></details>
          <button className="reassess" onClick={()=>void retry()} disabled={busy||running}>Assess current inventory again</button>
        </>}
        {review&&<section className="panel review" aria-label="Review booking"><h2>{disrupted?'Review your recovery itinerary':changing?'Review your booking change':'Review your booking'}</h2>{changing&&<ChangeComparison before={trip!.booking!.quote} after={review}/>} <p>{review.hotelName} · {review.roomDescription}</p><p>{mode(review.outboundMode)} outbound · {mode(review.returnMode)} return · October 16–18 · Two adults</p><p className="price">{money(review.totalCents)} <span className="small">total</span></p><Itinerary quote={review}/><p className="small muted">{changing?'Accepting replaces your reservation in one transaction. If the new trip is unavailable or its price changed, your current booking stays intact. The price difference is simulated; there are no exchange fees or real charges.':'This confirms a simulated booking against local inventory. Two seats per service and one room on each night will be reserved. Transfers are included as priced line items.'}</p><div className="actions"><button ref={reviewButton} className="primary" onClick={()=>void book()} disabled={busy||!actionable}>{busy?'Confirming…':disrupted?'Accept simulated recovery':changing?'Accept simulated booking change':'Confirm simulated booking'}</button><button onClick={()=>setReview(null)} disabled={busy}>Back to comparison</button></div></section>}
      </>}
      {screen==='booked'&&trip?.booking&&<><span className="badge">{disrupted?'Needs attention':'Simulated booking confirmed'}</span><h2 className="booked-title">{disrupted?'Your return journey needs a replacement.':'Your weekend is ready.'}</h2><p>{trip.booking.quote.hotelName} · {money(trip.booking.quote.totalCents)} total</p><div className="panel"><Itinerary quote={trip.booking.quote} canceledReturn={disrupted}/><p className="small muted">Booking reference: <code>{trip.booking.id}</code></p><p className="small">{disrupted?'Two outbound seats and both hotel nights remain reserved; the return service is canceled.':'Two seats per service · One room on October 16 and 17 · Transfers priced, not separately reserved'}</p></div><p className="small muted">Saved in the local database. This booking survives refresh and application restart.</p><button className="primary" onClick={edit} disabled={busy||running}>Change this trip</button>{!disrupted&&<details><summary>Demo controls</summary><p>Simulate cancellation of {trip.booking.quote.returnServiceId}. This affects every booking on this service in the shared demo and removes it from future searches. It remains canceled until the demo database is reset.</p><button disabled={busy||running} onClick={()=>void cancelReturn()}>Simulate return cancellation</button></details>}{trip.changes.length>0&&<details><summary>Booking change history ({trip.changes.length})</summary>{trip.changes.map(change=><section key={change.after.id}><p className="small muted">Accepted {new Date(change.after.createdAt).toLocaleString()} · {change.after.id}</p><ChangeComparison before={change.before.quote} after={change.after.quote} accepted/></section>)}</details>}</>}
    </main></div><footer>Fictional services and prices · No live supplier bookings · Shared local demo workspace</footer>
  </div>;
}
function TripCard({quote:q,label,reason,budget,primary=false,onChoose,disabled,current}:{quote:Quote;label:string;reason:string|null;budget:number;primary?:boolean;onChoose:(q:Quote)=>void;disabled:boolean;current?:Quote}){
  return <article className={'choice '+(primary?'recommended':'')}><span className="badge">{label}</span><h3>{q.hotelName}</h3><p className="small muted">{mode(q.outboundMode)} outbound · {mode(q.returnMode)} return</p><p>{q.roomDescription}</p><div className="price">{money(q.totalCents)}</div><div className="small muted">{money(budget-q.totalCents)} below your limit</div><dl><div><dt>Transport · Two travelers</dt><dd>{money(q.transportCents)}</dd></div><div><dt>Room · Two nights</dt><dd>{money(q.lodgingCents)}</dd></div><div><dt>Transfers · Entire trip</dt><dd>{money(q.transferCents)}</dd></div></dl><div>Hotel-ready Friday at {time(q.hotelReadyAt)}</div><div>{q.totalTransferMinutes} minutes of transfers across the trip</div><p className="reason">{reason}</p>{current&&<ChangeComparison before={current} after={q}/>}<details><summary>View itinerary</summary><Itinerary quote={q}/></details><button className={primary?'primary':''} disabled={disabled} onClick={()=>onChoose(q)}>Review {money(q.totalCents)} trip</button></article>;
}
function ChangeComparison({before,after,accepted=false}:{before:Quote;after:Quote;accepted?:boolean}){
  const delta=after.totalCents-before.totalCents;
  return <div className="change-comparison"><p><strong>{delta===0?'No price difference':money(Math.abs(delta))+(delta>0?' additional':' simulated refund')}</strong></p><table><caption>{accepted?'Previous booking → Accepted itinerary':'Current booking → Proposed itinerary'}</caption><thead><tr><th>Detail</th><th>{accepted?'Previous':'Current'}</th><th>{accepted?'Accepted':'Proposed'}</th></tr></thead><tbody><tr><th>Total</th><td>{money(before.totalCents)}</td><td>{money(after.totalCents)}</td></tr><tr><th>Hotel</th><td>{before.hotelName}</td><td>{after.hotelName}</td></tr><tr><th>Outbound</th><td>{mode(before.outboundMode)} {time(before.outboundDepartsAt)}</td><td>{mode(after.outboundMode)} {time(after.outboundDepartsAt)}</td></tr><tr><th>Leave hotel</th><td>{time(before.leaveHotelAt)}</td><td>{time(after.leaveHotelAt)}</td></tr><tr><th>Return</th><td>{mode(before.returnMode)} {time(before.returnDepartsAt)}</td><td>{mode(after.returnMode)} {time(after.returnDepartsAt)}</td></tr><tr><th>Back in Boston</th><td>{time(before.returnToOriginAt)}</td><td>{time(after.returnToOriginAt)}</td></tr></tbody></table></div>;
}
function Itinerary({quote:q,canceledReturn=false}:{quote:Quote;canceledReturn?:boolean}){
  return <div className="itinerary"><h3>Friday · Getting there</h3><ol><li><time>{time(q.startAt)}</time><span>Be at the Boston downtown meeting point{q.outboundMode==='flight'?' for your airport transfer':''}</span></li><li><time>{time(q.outboundDepartsAt)}</time><span>{mode(q.outboundMode)} departs Boston · {q.outboundServiceId}</span></li><li><time>{time(q.outboundArrivesAt)}</time><span>Arrive in New York · Arrival buffer and hotel transfer follow</span></li><li><time>{time(q.hotelArrivalAt)}</time><span>Arrive at {q.hotelName}</span></li><li><time>{time(q.hotelReadyAt)}</time><span>Room ready for check-in</span></li></ol><h3>{canceledReturn?'Sunday · Canceled return — original schedule':'Sunday · Heading back'}</h3><ol><li><time>11:00</time><span>Check out · Free luggage storage and lounge access</span></li><li><time>{time(q.leaveHotelAt)}</time><span>Leave the hotel for your return service</span></li><li><time>{time(q.returnDepartsAt)}</time><span>{mode(q.returnMode)} departs New York · {q.returnServiceId}</span></li><li><time>{time(q.returnArrivesAt)}</time><span>Arrive in Boston</span></li><li><time>{time(q.returnToOriginAt)}</time><span>Trip ends at the Boston downtown meeting point</span></li></ol></div>;
}
createRoot(document.getElementById('root')!).render(<React.StrictMode><App/></React.StrictMode>);
