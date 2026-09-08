"""Generate the four readable YAML manifests from shared closed schema shapes.
Run deliberately after editing this source; not required to build or run the app.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
def scalar(kind='string', **extra): return {'type':kind, **extra}
def obj(fields, **extra): return {'type':'object','properties':fields,'required':list(fields),'additionalProperties':False, **extra}
def arr(item=None): return {'type':'array','items':item or scalar()}
def evidence(schema, expression): return {**schema,'evidence':expression}
request = obj({k:scalar() for k in ['origin','destination','outboundDate','returnDate','hotelReadyBy','leaveHotelNoEarlierThan','returnToOriginBy']}
              | {k:scalar('integer') for k in ['partySize','rooms','budgetCents']}
              | {'allowedModes':arr(scalar(enum=['rail','flight'])),'priorities':arr(scalar(enum=['QUIET_ROOM','SHORT_TRANSFERS','LOWEST_TOTAL']))})
root_input = obj({'assessmentId':scalar(),'request':request})
choice = obj({'candidateId':scalar(description='Exact candidateId from evaluated Java trip options; never invent or modify it.'),'rationale':scalar()},nullable=True)
root_evidence='planTransport and assessStay and assessTripLogistics'
manifests = [
{
 'name':'planTrip','description':'Coordinate transport, stay and local logistics specialists to recommend a validated complete trip and one meaningful alternative.',
 'model':'planner','planning_mode':True,'concurrency':True,'max_steps':6,
 'allowed_skills':[{'name':n,'required':True,'max_tasks':1} for n in ['planTransport','assessStay','assessTripLogistics']],
 'prompt':'''Plan one immutable saved trip assessment. The assessmentId is an application-owned lookup key: pass it exactly unchanged to every specialist.
Use exactly these tasks: planTransport and assessStay independently, grouped together with the same parallelGroup; then assessTripLogistics depending on both.
Pass assessmentId and the full original request to transport and stay. After both finish, pass only assessmentId to logistics. The Java searches persist immutable catalog results keyed by that assessment; logistics consumes those exact results without copying inventory IDs through the model.
Do not call Java grandchildren directly. Do not skip logistics when a catalog is empty; it establishes the blockers.
The logistics specialist evaluates all combinations and returns a compact preference-based recommendation and alternative. Preserve its exact candidateIds and status in your final result; synthesize its short explanation without inventing options.
If logistics returns NO_FEASIBLE_TRIP, use null choices and explain its verified blockers. Otherwise return OPTIONS with its recommended and optional alternative choice. Do not override the saved hard budget or deadlines.
Explain tradeoffs with the actual facts, not fabricated scores. No booking or inventory writes occur during this assessment.
In rationale and explanation use only qualitative traveler-facing language. The application displays all exact amounts, savings, times and counts. Do not include any digits, currency symbols, numerical comparisons (even spelled out), priority enum names, tool names or inventory IDs in prose. Say 'less expensive', 'quiet room' or 'shorter transfers' instead. This prose rule is separate from exact candidateId and assessmentId fields.''',
 'input_schema':root_input,
 'output_schema':obj({'assessmentId':scalar(),'status':evidence(scalar(enum=['OPTIONS','NO_FEASIBLE_TRIP']),root_evidence),
                      'recommended':evidence(choice,root_evidence),'alternative':evidence(choice,root_evidence),'explanation':evidence(scalar(),root_evidence)})
},
{
 'name':'planTransport','description':'Select relevant rail and/or air catalog searches and preserve all eligible service IDs for whole-trip evaluation.',
 'model':'planner','planning_mode':True,'concurrency':True,'max_steps':4,
 'allowed_skills':[{'name':'searchRailServices','max_tasks':1},{'name':'searchFlightServices','max_tasks':1}],
 'prompt':'''Read request.allowedModes. Search EACH allowed mode exactly once: rail uses searchRailServices, flight uses searchFlightServices. Never search a disallowed mode.
When both modes are allowed, group their independent searches in a parallelGroup. For rail-only or flight-only, plan only the applicable search.
Each Java search takes only assessmentId, copied unchanged. Forward no scenario key or rewritten requirements.
Merge all returned outbound IDs into outboundServiceIds and all return IDs into returnServiceIds without duplicates. Do not prune by fare, arrival time, or personal preference: downstream hotel transfers and check-in determine whole-trip feasibility.
Preserve empty lists when inventory is empty. Describe relevant mode tradeoffs briefly; do not invent facts.''',
 'input_schema':root_input,
 'output_schema':obj({'assessmentId':scalar(),'outboundServiceIds':evidence(arr(),'searchRailServices or searchFlightServices'),
                      'returnServiceIds':evidence(arr(),'searchRailServices or searchFlightServices'),'considerations':evidence(arr(),'searchRailServices or searchFlightServices')})
},
{
 'name':'assessStay','description':'Investigate eligible rooms and quiet-room versus price tradeoffs, preserving the complete hotel catalog for logistics.',
 'model':'planner','allowed_skills':[{'name':'searchHotels'}],
 'prompt':'''Call searchHotels with the exact assessmentId. Retain EVERY returned hotel ID in hotelIds, without duplicates. Do not independently select just the cheapest room: transfer costs may reverse its price advantage, and quiet-room preference matters.
Use request.priorities to describe room tradeoffs. Quiet-room guarantees must come from Java catalog data. No loyalty capability exists in this slice. Return an empty hotelIds array if no room is available.''',
 'input_schema':root_input,
 'output_schema':obj({'assessmentId':scalar(),'hotelIds':evidence(arr(),'searchHotels'),'considerations':evidence(arr(),'searchHotels')})
},
{
 'name':'assessTripLogistics','description':'Evaluate all complete trip candidates, select a preference-based recommendation and meaningful alternative, and return a compact decision to the coordinator.',
 'model':'planner','allowed_skills':[{'name':'evaluateTripOptions'}],
 'prompt':'''Call evaluateTripOptions with the exact assessmentId supplied by your parent. Java loads the completed search results from the assessment record. Missing catalog results or other tool errors are execution failures, never trip infeasibility.
Java returns selections first, followed by the authoritative confirmed request and full evaluation. Copy selections.recommendedCandidateId exactly into recommended.candidateId. An alternative may use ONLY an ID from selections.alternativeCandidateIds. If that array is empty, alternative MUST be JSON null, not an object. These selections encode the saved preferences and meaningful tradeoffs; do not substitute IDs from elsewhere in the evaluation. Use the full evaluation to explain the selected trips.
Choose the recommended candidate by the confirmed request.priorities in order: QUIET_ROOM prefers quietRoom=true; SHORT_TRANSFERS prefers lower totalTransferMinutes; LOWEST_TOTAL prefers lower totalCents. Budget and deadlines are hard constraints already checked by Java. Java returns trips sorted with feasible candidates first, then by these ordered preferences. Recommend the first candidate with an empty violations array. Do not replace it with a later candidate; this order is validated by the application.
Offer one distinct meaningful feasible alternative. For a quiet-room recommendation choose the cheapest feasible trip as the budget alternative. For a lowest-price recommendation offer a quiet-room upgrade if affordable. An alternative MUST improve at least one saved preference compared with the recommendation: a quiet-room guarantee, lower totalTransferMinutes, or lower totalCents. If none improves, return null; never offer a worse candidate merely to fill the alternative field. In particular, if the recommendation is already quiet, cheapest, and has the shortest transfers, the alternative MUST be null. Never call a higher-priced candidate less expensive. Never invent candidateIds.
If there are no feasible candidates, return NO_FEASIBLE_TRIP and null choices, explaining the Java global blockers. Money is USD cents for the entire party and stay, not per traveler.
Return a COMPACT decision, ideally under 900 characters: status and choices first, rationale at most one short sentence per choice, explanation at most two short sentences. Do not echo the full catalog or all rejected options to the planner. The application retains and displays authoritative details. Do not reserve inventory.
In rationale and explanation use only qualitative traveler-facing language. The application displays all exact amounts, savings, times and counts. Do not include any digits, currency symbols, numerical comparisons (even spelled out), priority enum names, tool names or inventory IDs in prose. Say 'less expensive', 'quiet room' or 'shorter transfers' instead. This prose rule does not apply to exact candidateId fields.''',
 'input_schema':obj({'assessmentId':scalar()}),
 'output_schema':obj({'status':evidence(scalar(enum=['OPTIONS','NO_FEASIBLE_TRIP']),'evaluateTripOptions'),
                      'recommended':evidence(choice,'evaluateTripOptions'),'alternative':evidence(choice,'evaluateTripOptions'),
                      'explanation':evidence(scalar(),'evaluateTripOptions')})
}]

def yaml(value, indent=0):
    pad=' '*indent
    if isinstance(value,dict):
        lines=[]
        for key,item in value.items():
            if isinstance(item,(dict,list)) and item:
                lines.append(f'{pad}{key}:\n{yaml(item,indent+2)}')
            elif isinstance(item,str) and '\n' in item:
                lines.append(f'{pad}{key}: |\n'+'\n'.join(' '*(indent+2)+line for line in item.splitlines()))
            else: lines.append(f'{pad}{key}: {json.dumps(item)}')
        return '\n'.join(lines)
    return '\n'.join(pad+'-\n'+yaml(item,indent+2) if isinstance(item,dict) else pad+'- '+json.dumps(item) for item in value)

folder=ROOT/'src/main/resources/skills'
folder.mkdir(parents=True,exist_ok=True)
for manifest in manifests:
    manifest['output_schema_max_retries']=2
    (folder/(manifest['name']+'.yml')).write_text('# Generated by scripts/generate-skill-manifests.py\n'+yaml(manifest)+'\n',encoding='utf-8')
print('Generated four skill manifests.')
