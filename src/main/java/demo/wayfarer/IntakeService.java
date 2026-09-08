package demo.wayfarer;

import ai.loomspan.api.SkillTemplate;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import static demo.wayfarer.Contracts.*;
import static demo.wayfarer.IntakeContracts.*;

@Service
public class IntakeService {
    private final TripStore store;
    private final TripCalculator calculator;
    private final JdbcTemplate db;
    private final SkillTemplate skills;
    private final Semaphore slots=new Semaphore(2);
    public IntakeService(TripStore store,TripCalculator calculator,JdbcTemplate db,SkillTemplate skills) {this.store=store;this.calculator=calculator;this.db=db;this.skills=skills;}
    static Baseline baseline(TripView trip) {
        var b=trip.booking();return new Baseline(trip.revision(),trip.request(),b==null?null:b.id(),trip.catalogVersion(),b==null?null:b.quote().hotelName(),b!=null&&b.quote().quietRoom(),b==null?null:b.quote().totalCents(),trip.disruption()!=null,trip.disruption()==null?null:b.quote().returnMode());
    }
    private Baseline modelBaseline(Baseline b) {
        return new Baseline(b.revision(),b.request(),b.bookingId()==null?"":b.bookingId(),b.catalogVersion(),b.hotelName()==null?"":b.hotelName(),b.quietRoom(),b.bookedTotalCents()==null?0L:b.bookedTotalCents(),b.disrupted(),b.canceledReturnMode()==null?"":b.canceledReturnMode());
    }
    public View latest(String tripId) {
        store.get(tripId);
        var rows=db.query("select id from intake_draft where trip_id=? order by created_at desc",(rs,n)->rs.getString(1),tripId);
        return rows.isEmpty()?null:get(tripId,rows.getFirst());
    }
    public View get(String tripId,String id) {
        var rows=db.query("select view_json,confirmed_revision from intake_draft where id=? and trip_id=?",(rs,n)->store.decode(rs.getString(1),View.class).confirmed(rs.getObject(2,Integer.class)),id,tripId);
        if(rows.isEmpty()) throw ApiProblem.missing();return rows.getFirst();
    }
    // The model call deliberately runs outside a database transaction.
    public View interpret(String tripId,Command command) {
        if(command==null || command.message()==null || command.message().isBlank() || command.message().length()>2000) throw ApiProblem.invalid("Enter a trip change of at most 2,000 characters.");
        var baseline=baseline(store.get(tripId));var turns=new ArrayList<Turn>();
        if(command.parentId()!=null) {
            var parent=get(tripId,command.parentId());
            if(!"CLARIFY".equals(parent.status()) || parent.confirmedRevision()!=null) throw ApiProblem.conflict("Start a new change after this interpretation.");
            if(!parent.baseline().equals(baseline)) throw ApiProblem.conflict("The trip changed. Start a new conversation using its current requirements.");
            turns.addAll(parent.conversation());
        }
        if(turns.size()>=10) throw ApiProblem.invalid("Start a new change after five clarification rounds.");
        turns.add(new Turn("traveler",command.message().strip()));
        if(!slots.tryAcquire()) throw ApiProblem.conflict("Both interpretation slots are busy. Retry shortly.");
        Result result;AtomicReference<String> session=new AtomicReference<>();
        try {result=store.decode(skills.invoke("interpretTripChange",new Input(modelBaseline(baseline),List.copyOf(turns)),view->session.set(view.sessionId())),Result.class);}
        catch(RuntimeException e) {throw ApiProblem.conflict("The interpretation could not finish. Your request and booking are unchanged; retry shortly.");}
        finally {slots.release();}
        var view=validate(tripId,baseline,turns,result,session.get());
        db.update("insert into intake_draft (id,trip_id,view_json,created_at) values (?,?,?,?)",view.id(),tripId,store.encode(view),TripStore.now());
        return view;
    }
    View validate(String tripId,Baseline baseline,List<Turn> turns,Result result,String session) {
        if(result==null || result.status()==null || !List.of("READY","CLARIFY","UNSUPPORTED").contains(result.status()) || result.message()==null || result.message().isBlank() || result.message().length()>1000) throw ApiProblem.invalid("The interpretation returned an invalid response. Nothing was changed.");
        TripRequest proposed=null;List<Difference> differences=List.of();
        if(result.status().equals("READY")) {
            if(result.patch()==null) throw ApiProblem.invalid("The interpretation omitted its proposed changes.");
            proposed=apply(baseline.request(),result.patch());calculator.validate(proposed);
            differences=differences(baseline.request(),proposed);
            if(differences.isEmpty()) throw ApiProblem.invalid("No request changes were identified. Nothing was saved.");
        } else if(result.patch()!=null) throw ApiProblem.invalid("An unclear or unsupported request cannot include executable changes.");
        var conversation=new ArrayList<>(turns);conversation.add(new Turn("assistant",result.message()));
        return new View(TripStore.id(),tripId,baseline,List.copyOf(conversation),result.status(),result.message(),proposed,differences,session,null);
    }
    private TripRequest apply(TripRequest r,Patch p) {
        if(p.budgetCents()!=null && p.budgetDeltaCents()!=null) throw ApiProblem.invalid("A budget change must be absolute or relative, not both.");
        long budget;
        try {budget=p.budgetCents()!=null?p.budgetCents():p.budgetDeltaCents()!=null?Math.addExact(r.budgetCents(),p.budgetDeltaCents()):r.budgetCents();}
        catch(ArithmeticException e) {throw ApiProblem.invalid("The budget change is too large.");}
        var priorities=r.priorities();
        if(p.firstPriority()!=null) {
            if(!List.of("QUIET_ROOM","SHORT_TRANSFERS","LOWEST_TOTAL").contains(p.firstPriority())) throw ApiProblem.invalid("Unsupported preference.");
            var ordered=new ArrayList<String>();ordered.add(p.firstPriority());r.priorities().stream().filter(v->!v.equals(p.firstPriority())).forEach(ordered::add);priorities=List.copyOf(ordered);
        }
        return new TripRequest(r.origin(),r.destination(),r.outboundDate(),r.returnDate(),r.partySize(),r.rooms(),budget,
            stamp(r.outboundDate(),r.hotelReadyBy(),p.hotelReadyTime()),stamp(r.returnDate(),r.leaveHotelNoEarlierThan(),p.leaveHotelTime()),stamp(r.returnDate(),r.returnToOriginBy(),p.returnHomeTime()),p.allowedModes()==null?r.allowedModes():p.allowedModes(),priorities);
    }
    private String stamp(String date,String current,String time) {
        if(time==null) return current;
        if(!time.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]")) throw ApiProblem.invalid("Use an exact time in hours and minutes.");
        return date+"T"+time+":00-04:00";
    }
    private List<Difference> differences(TripRequest a,TripRequest b) {
        var result=new ArrayList<Difference>();
        diff(result,"Total budget",money(a.budgetCents()),money(b.budgetCents()));
        diff(result,"Hotel-ready Friday",clock(a.hotelReadyBy()),clock(b.hotelReadyBy()));
        diff(result,"Leave hotel Sunday no earlier than",clock(a.leaveHotelNoEarlierThan()),clock(b.leaveHotelNoEarlierThan()));
        diff(result,"Back in Boston Sunday by",clock(a.returnToOriginBy()),clock(b.returnToOriginBy()));
        diff(result,"Allowed travel modes",String.join(", ",a.allowedModes()),String.join(", ",b.allowedModes()));
        diff(result,"Preference order",priorities(a.priorities()),priorities(b.priorities()));
        return List.copyOf(result);
    }
    private String money(long cents) {return "$%.2f".formatted(cents/100.0);}
    private String clock(String value) {return TripCalculator.time(value).toLocalTime()+" Eastern";}
    private String priorities(List<String> priorities) {return String.join(" → ",priorities.stream().map(p->switch(p){case "QUIET_ROOM"->"Quiet room";case "SHORT_TRANSFERS"->"Shorter transfers";default->"Lowest total";}).toList());}
    private void diff(List<Difference> target,String field,String a,String b) {if(!a.equals(b))target.add(new Difference(field,a,b));}
    @Transactional
    public TripView confirm(String tripId,String draftId) {
        var locked=db.queryForList("select id from trip where id=? for update",tripId);if(locked.isEmpty())throw ApiProblem.missing();
        db.queryForList("select id from catalog_guard where id=1 for update");
        var draft=get(tripId,draftId);var current=store.get(tripId);
        if(draft.confirmedRevision()!=null) {
            if(current.revision()!=draft.confirmedRevision())throw ApiProblem.conflict("This change was already confirmed; the trip has since changed.");
            return current;
        }
        if(!draft.status().equals("READY") || draft.proposedRequest()==null) throw ApiProblem.conflict("Only an explicit proposed change can be confirmed.");
        if(!draft.baseline().equals(baseline(current))) throw ApiProblem.conflict("The request, booking, or service availability changed. Interpret the change again before confirming.");
        var revised=store.revise(tripId,new RevisionCommand(current.revision(),draft.proposedRequest()));
        db.update("update intake_draft set confirmed_revision=? where id=?",revised.revision(),draftId);
        return revised;
    }
}
