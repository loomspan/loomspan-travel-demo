import {useEffect,useState} from 'react';
import type {Trip,TripRequest} from './types';

type Draft={id:string;baseline:{revision:number;bookingId:string|null;catalogVersion:number};conversation:{role:string;text:string}[];status:string;message:string;proposedRequest:TripRequest|null;differences:{field:string;before:string;after:string}[];sessionId:string|null;confirmedRevision:number|null};
async function request<T>(path:string,method='GET',body?:unknown):Promise<T>{
  const response=await fetch('/api'+path,{method,headers:body===undefined?{}:{'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body)});
  const data=await response.json();if(!response.ok)throw new Error(data.message??'The interpretation could not finish.');return data;
}
export function Conversation({trip,disabled,onConfirm}:{trip:Trip;disabled:boolean;onConfirm:(id:string)=>Promise<void>}){
  const [draft,setDraft]=useState<Draft|null>(null),[message,setMessage]=useState(''),[busy,setBusy]=useState(false),[error,setError]=useState(''),[loaded,setLoaded]=useState(false);
  useEffect(()=>{let canceled=false;request<{draft:Draft|null}>('/trips/'+trip.id+'/intakes/latest').then(value=>{if(!canceled)setDraft(value.draft);}).catch(e=>{if(!canceled)setError(e.message);}).finally(()=>{if(!canceled)setLoaded(true);});return()=>{canceled=true;};},[trip.id]);
  const stale=!!draft&&(draft.baseline.revision!==trip.revision||draft.baseline.bookingId!==(trip.booking?.id??null)||draft.baseline.catalogVersion!==trip.catalogVersion);
  const clarifying=draft?.status==='CLARIFY'&&!stale;
  async function interpret(event:React.FormEvent){
    event.preventDefault();setBusy(true);setError('');
    try{setDraft(await request<Draft>('/trips/'+trip.id+'/intakes','POST',{message,parentId:clarifying?draft!.id:null}));setMessage('');}
    catch(e){setError((e as Error).message);}finally{setBusy(false);}
  }
  async function confirm(){if(!draft)return;setBusy(true);setError('');try{await onConfirm(draft.id);setDraft((await request<{draft:Draft|null}>('/trips/'+trip.id+'/intakes/latest')).draft);}catch(e){setError((e as Error).message);}finally{setBusy(false);}}
  function reset(){setDraft(null);setMessage('');setError('');}
  return <details id="trip-conversation" className="panel conversation"><summary>Describe a trip change</summary><p>Tell Loomspan what you want to change. Review the exact differences before saving requirements and comparing trips. Booking acceptance remains a separate step.</p>
    <p className="small muted">Examples: “Keep the quiet hotel, but get us home earlier.” “I can spend another $100.” “Flights are okay if the train is canceled.” Relative budget changes use your saved budget. All times are Eastern.</p>
    {draft&&<><div className="conversation-history" aria-label="Trip change conversation">{draft.conversation.map((turn,i)=><p key={i}><strong>{turn.role==='traveler'?'You':'Wayfarer'}:</strong> {turn.text}</p>)}</div>
      {draft.confirmedRevision!==null?<p role="status">Saved as request revision {draft.confirmedRevision}. Booking acceptance is still separate.</p>:stale?<p className="notice" role="status">The trip changed since this interpretation. Start a new change using the current requirements.</p>:draft.status==='READY'?<><h3>Review proposed requirements</h3><div className="change-comparison"><table><thead><tr><th>Requirement</th><th>Saved</th><th>Proposed</th></tr></thead><tbody>{draft.differences.map(diff=><tr key={diff.field}><th>{diff.field}</th><td>{diff.before}</td><td>{diff.after}</td></tr>)}</tbody></table></div><p className="small">Other saved requirements stay the same.{trip.booking?' Your booked hotel is retained.':''}{trip.disruption?' Recovery also retains your outbound service.':''}</p><button className="primary" disabled={busy||disabled} onClick={()=>void confirm()}>Confirm requirements and compare trips</button></>:draft.status==='UNSUPPORTED'?<p className="notice">This request cannot be applied in the current demo.</p>:null}
      {draft.sessionId&&<p className="small muted">Interpretation session: <code>{draft.sessionId}</code></p>}
      <button type="button" disabled={busy||disabled} onClick={reset}>Start a new change</button>
    </>}
    {(!draft||clarifying)&&<form onSubmit={interpret}><label>{clarifying?'Answer the question':'What would you like to change?'}<textarea required maxLength={2000} rows={3} value={message} disabled={busy||disabled||!loaded} onChange={e=>setMessage(e.target.value)}/></label><button type="submit" disabled={busy||disabled||!loaded||!message.trim()}>{busy?'Interpreting with Loomspan…':clarifying?'Interpret my answer':'Review my requested change'}</button></form>}
    {busy&&<p role="status">Working… Your booking is unchanged.</p>}{error&&<p className="notice error" role="alert">{error}</p>}
  </details>;
}
