import type {Assessment} from './types';

const roles=[
  {route:'planTransport',title:'Transport specialist',description:'Searches allowed rail and flight modes and passes eligible services to the complete-trip evaluation.',leaves:['searchRailServices','searchFlightServices']},
  {route:'assessStay',title:'Stay specialist',description:'Finds eligible rooms and their quiet-room guarantees, retaining hotel choices for logistics.',leaves:['searchHotels']},
  {route:'assessTripLogistics',title:'Logistics specialist',description:'Combines the search results with transfers and check-in times. Java checks the saved budget and deadlines before preferences determine the recommendation.',leaves:['evaluateTripOptions']}
];
export function Coordination({assessment:a}:{assessment:Assessment}){
  const finished=(route:string)=>a.events.some(e=>e.route===route&&e.type==='SKILL_FINISHED');
  return <section className="coordination panel" aria-label="Skill coordination"><h3>How the skills contributed</h3><p className="small muted">Transport and stay are planned independently. Logistics depends on both, then the trip coordinator assembles the proposal. Status below comes from recorded execution events; it is not a live progress feed.</p>
    <div className="skill-contributions">{roles.map(role=><article key={role.route}><span className="badge">{finished(role.route)?'Completion recorded':'No completion recorded'}</span><h4>{role.title}</h4><p>{role.description}</p><p className="small muted">Recorded catalog work: {role.leaves.filter(finished).join(', ')||'None'}</p></article>)}</div>
    {a.result&&<p className="coordination-result"><strong>{a.result.feasibleCount} of {a.result.consideredCount} complete combinations meet the saved requirements.</strong> {a.result.status==='NO_FEASIBLE_TRIP'?'No bookable proposal was produced. Review the blockers below.':'The proposal below uses validated options and your saved preference order.'}</p>}
    <p className="small">Trip coordinator: {finished('planTrip')?'completion recorded':'no completion recorded'}. Planning does not reserve inventory; booking acceptance checks it again.</p>
    <details><summary>Execution evidence</summary><p className="small">Loomspan session: <code>{a.sessionId??'Unavailable'}</code></p><p className="small muted">These events establish which skills ran. Open this session in Loomspan Console for plans and overlap details. Role descriptions above explain the configured responsibilities, not individual model transcripts.</p><ul className="events">{a.events.map((event,i)=><li key={i}><time>{new Date(event.timestamp).toLocaleTimeString()}</time>{event.type==='SKILL_STARTED'?'Started':event.type==='SKILL_FINISHED'?'Finished':event.type} · {event.route??event.frameId??'Skill'}</li>)}</ul></details>
  </section>;
}
