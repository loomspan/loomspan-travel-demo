import {useState} from 'react';
import type {Assessment,Trip} from './types';

type Scenario='plan'|'change'|'recover';
const scenarios:{id:Scenario;title:string;description:string;steps:string[]}[]=[
  {id:'plan',title:'Plan a weekend',description:'See specialists build one complete trip.',steps:['Load the example request and confirm it to compare trips.','Read how transport, stay, and logistics contributed to the result.','Review an option, then explicitly confirm the simulated booking.']},
  {id:'change',title:'Change your plans',description:'Turn a conversation into a reviewed booking change.',steps:['Select a booked trip, or plan and book a weekend first.','In “Describe a trip change”, ask: “Keep the quiet hotel, but get us home earlier.” Answer with an exact Eastern deadline.','Review the proposed requirements and confirm them to run planning again.','Compare the current and proposed itineraries, then separately accept the booking change.']},
  {id:'recover',title:'Handle a cancellation',description:'Find a way home while retaining the outbound trip and hotel.',steps:['Select a booked trip, or plan and book a weekend first.','Open Demo controls on the booked trip and simulate its return cancellation.','Choose “Find a replacement with Loomspan”. If no trip fits, review a suggested requirement change and confirm it to reassess.','Review and explicitly accept a recovery itinerary; inspect booking change history.']}
];
export function DemoGuide({trip,assessment,disabled,onNew,onNavigate}:{trip:Trip|null;assessment:Assessment|undefined;disabled:boolean;onNew:()=>void;onNavigate:(target:'request'|'conversation'|'booked'|'compare')=>void}){
  const [selected,setSelected]=useState<Scenario|null>(()=>{const saved=localStorage.getItem('wayfarer.guide');return saved==='plan'||saved==='change'||saved==='recover'?saved:null;});
  const scenario=scenarios.find(s=>s.id===selected);
  const hasProposal=assessment?.status==='SUCCEEDED';
  let next='Load the example request, then confirm it to start a real assessment.';
  let target:'request'|'conversation'|'booked'|'compare'='request';
  let action='Go to your request';
  if(selected==='plan'&&trip?.booking){next='This trip is booked. Try a conversational change, or start a new weekend to demonstrate initial planning.';target='booked';action='View booked trip';}
  else if(selected==='plan'&&hasProposal){next='The assessment is ready. Explore skill contributions and review an available option.';target='compare';action='View comparison';}
  else if(selected==='change'&&trip?.booking&&hasProposal&&assessment?.baseBookingId===trip.booking.id){next='Your revised assessment is ready. Compare the itineraries and review an available replacement before accepting it.';target='compare';action='View booking changes';}
  else if(selected==='change'&&trip?.booking){next='Describe your change and review the exact requirements before confirming. Your current booking stays reserved during planning.';target='conversation';action='Describe a trip change';}
  else if(selected==='recover'&&trip?.disruption){next='This trip needs recovery. Find a replacement with Loomspan, then review any blockers or proposed itinerary.';target='booked';action='View affected booking';}
  else if(selected==='recover'&&trip?.booking){next='Open Demo controls below the booked itinerary to simulate a cancellation when you are ready.';target='booked';action='View booked trip';}
  else if(selected!=='plan'){next='This scenario needs a booking. Select a booked trip from your trips, or finish planning and booking this weekend first.';if(hasProposal){target='compare';action='View comparison';}}
  return <section className="demo-guide" aria-label="Guided demo scenarios"><div className="row"><div><h2>Explore the demo</h2><p className="small muted">Choose a walkthrough. Each uses the real trip workflow and shared local inventory.</p></div>{scenario&&<button onClick={()=>{setSelected(null);localStorage.removeItem('wayfarer.guide');}}>Hide walkthrough</button>}</div>
    <div className="scenario-choices">{scenarios.map(s=><button key={s.id} aria-pressed={selected===s.id} onClick={()=>{setSelected(s.id);localStorage.setItem('wayfarer.guide',s.id);}}><strong>{s.title}</strong><span>{s.description}</span></button>)}</div>
    {scenario&&<div className="walkthrough"><h3>{scenario.title} walkthrough</h3><ol>{scenario.steps.map(step=><li key={step}>{step}</li>)}</ol>{selected==='change'&&<p className="small">Try 21:30 Eastern if the saved return deadline is later. Results depend on the saved requirements and available inventory.</p>}{selected==='recover'&&<p className="small">Cancellation affects every booking on that return service and persists in this database. Selecting this guide does not cancel anything. For repeatable presentations, use a separate demo database; see the README.</p>}<p><strong>For this trip:</strong> {next}</p><div className="actions"><button disabled={disabled} onClick={()=>onNavigate(target)}>{action}</button><button disabled={disabled} onClick={onNew}>Start a new weekend</button></div></div>}
  </section>;
}
