package demo.wayfarer;

import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;
import static demo.wayfarer.Contracts.*;

@Component
public class TripCalculator {
    static final ZoneId ZONE = ZoneId.of("America/New_York");
    public static TripRequest example() {
        return new TripRequest("BOS", "NYC", "2026-10-16", "2026-10-18", 2, 1, 120000,
            "2026-10-16T17:00:00-04:00", "2026-10-18T16:00:00-04:00", "2026-10-18T23:00:00-04:00",
            List.of("rail", "flight"), List.of("QUIET_ROOM", "SHORT_TRANSFERS", "LOWEST_TOTAL"));
    }
    public void validate(TripRequest r) {
        if (r == null || !"BOS".equals(r.origin()) || !"NYC".equals(r.destination()) ||
            !"2026-10-16".equals(r.outboundDate()) || !"2026-10-18".equals(r.returnDate()) || r.partySize()!=2 || r.rooms()!=1)
            throw ApiProblem.invalid("This slice supports Boston–New York, October 16–18, 2026, two adults and one room.");
        if(r.budgetCents()<1 || r.budgetCents()>10000000) throw ApiProblem.invalid("Budget must be between $0.01 and $100,000.");
        if(r.allowedModes()==null || r.allowedModes().isEmpty() || !Set.of("rail","flight").containsAll(r.allowedModes()) || new HashSet<>(r.allowedModes()).size()!=r.allowedModes().size())
            throw ApiProblem.invalid("Choose rail, flight, or both without duplicates.");
        if(r.priorities()==null || r.priorities().isEmpty() || !Set.of("QUIET_ROOM","SHORT_TRANSFERS","LOWEST_TOTAL").containsAll(r.priorities()) || new HashSet<>(r.priorities()).size()!=r.priorities().size())
            throw ApiProblem.invalid("Choose supported, unique trip priorities.");
        try {
            OffsetDateTime ready=time(r.hotelReadyBy()), leave=time(r.leaveHotelNoEarlierThan()), back=time(r.returnToOriginBy());
            if(!ready.toLocalDate().equals(LocalDate.parse(r.outboundDate())) || !leave.toLocalDate().equals(LocalDate.parse(r.returnDate())) ||
                !back.toLocalDate().equals(LocalDate.parse(r.returnDate())) || leave.toLocalTime().isBefore(LocalTime.of(11,0)) || !back.isAfter(leave))
                throw new IllegalArgumentException();
        } catch(RuntimeException e) { throw ApiProblem.invalid("Use valid Eastern timestamps on the travel dates, with Sunday hotel departure at or after 11:00 and Boston return afterward."); }
    }
    static OffsetDateTime time(String value) {
        OffsetDateTime date=OffsetDateTime.parse(value);
        if(!date.getOffset().equals(ZONE.getRules().getOffset(date.toInstant()))) throw new IllegalArgumentException("Incorrect Eastern offset");
        return date;
    }
    public List<ServiceOption> eligibleServices(Snapshot s, String direction) {
        return s.services().stream().filter(x->x.direction().equals(direction) && s.request().allowedModes().contains(x.mode()) && x.seats()>=s.request().partySize()).toList();
    }
    public List<HotelOption> eligibleHotels(Snapshot s) {
        return s.hotels().stream().filter(h->h.roomCapacity()>=s.request().partySize() && h.roomsPerNight()>=s.request().rooms()).toList();
    }
    public Evaluation evaluate(Snapshot s) {
        validate(s.request());
        List<ServiceOption> out=eligibleServices(s,"outbound"), back=eligibleServices(s,"return");
        List<HotelOption> hotels=eligibleHotels(s);
        if(out.size()>4 || back.size()>4 || hotels.size()>4) throw ApiProblem.conflict("CATALOG_LIMIT_EXCEEDED: the catalog cannot be evaluated completely.");
        List<Violation> blockers=new ArrayList<>();
        if(out.isEmpty()) blockers.add(new Violation("NO_OUTBOUND","No outbound service has enough seats in the selected modes."));
        if(back.isEmpty()) blockers.add(new Violation("NO_RETURN","No return service has enough seats in the selected modes."));
        if(hotels.isEmpty()) blockers.add(new Violation("NO_ROOM","No room is available for both nights."));
        List<CheckedTrip> trips=new ArrayList<>();
        for(var o:out) for(var b:back) for(var h:hotels) trips.add(calculate(s,o,b,h));
        if(!trips.isEmpty() && trips.stream().noneMatch(t->t.violations().isEmpty())) {
            OptionalLong cheapest=trips.stream().filter(t->t.violations().stream().allMatch(v->v.code().equals("OVER_BUDGET"))).mapToLong(CheckedTrip::totalCents).min();
            if(cheapest.isPresent()) blockers.add(new Violation("OVER_BUDGET", "The least expensive trip meeting your timing requirements is $%.2f, $%.2f over budget.".formatted(cheapest.getAsLong()/100.0,(cheapest.getAsLong()-s.request().budgetCents())/100.0)));
            else trips.stream().flatMap(t->t.violations().stream()).filter(v->!v.code().equals("OVER_BUDGET")).map(Violation::code).distinct()
                .forEach(code->blockers.add(new Violation(code, switch(code) {
                    case "HOTEL_DEADLINE" -> "Available hotel-ready times miss your Friday deadline; rooms cannot be ready before 15:00 check-in.";
                    case "EARLY_RETURN_DEPARTURE" -> "Return transfers require leaving the hotel earlier than your Sunday limit.";
                    default -> "Available return journeys do not meet your latest Boston return time.";
                })));
        }
        trips.sort(Comparator.comparing((CheckedTrip t)->!t.violations().isEmpty()).thenComparing(preferenceOrder(s.request())));
        return new Evaluation(List.copyOf(trips),List.copyOf(blockers),trips.size(),true);
    }
    private Comparator<CheckedTrip> preferenceOrder(TripRequest request) {
        Comparator<CheckedTrip> order=(a,b)->0;
        for(String priority:request.priorities()) order=order.thenComparing(switch(priority) {
            case "QUIET_ROOM" -> Comparator.comparing(t->!t.quietRoom());
            case "SHORT_TRANSFERS" -> Comparator.comparingInt(CheckedTrip::totalTransferMinutes);
            default -> Comparator.comparingLong(CheckedTrip::totalCents);
        });
        return order;
    }
    public SelectionOptions selectionOptions(TripRequest request,Evaluation evaluation) {
        var feasible=evaluation.trips().stream().filter(t->t.violations().isEmpty()).toList();
        if(feasible.isEmpty()) return new SelectionOptions(null,List.of());
        var best=feasible.getFirst();
        return new SelectionOptions(best.candidateId(),feasible.stream().filter(t->improvesPreference(request,t,best)).map(CheckedTrip::candidateId).toList());
    }
    private boolean improvesPreference(TripRequest request,CheckedTrip other,CheckedTrip recommended) {
        return request.priorities().stream().anyMatch(p->switch(p) {
            case "QUIET_ROOM" -> other.quietRoom() && !recommended.quietRoom();
            case "SHORT_TRANSFERS" -> other.totalTransferMinutes()<recommended.totalTransferMinutes();
            default -> other.totalCents()<recommended.totalCents();
        });
    }
    private CheckedTrip calculate(Snapshot s, ServiceOption o, ServiceOption b, HotelOption h) {
        var r=s.request(); var om=s.modes().get(o.mode()); var bm=s.modes().get(b.mode());
        var ot=h.transfers().get(o.mode()); var bt=h.transfers().get(b.mode());
        long nights=java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(r.outboundDate()),LocalDate.parse(r.returnDate()));
        long transport=r.partySize()*(o.farePerPersonCents()+b.farePerPersonCents());
        long lodging=nights*r.rooms()*h.nightlyRoomCents();
        long transfer=ot.partyCents()+bt.partyCents()+om.bostonTransferPartyCents()+bm.bostonTransferPartyCents();
        long total=transport+lodging+transfer;
        var arrival=time(o.arrives()).plusMinutes(om.arrivalBufferMinutes()+ot.minutes());
        var checkin=LocalDate.parse(r.outboundDate()).atTime(LocalTime.parse(h.checkIn())).atZone(ZONE).toOffsetDateTime();
        var ready=arrival.isAfter(checkin)?arrival:checkin;
        var leave=time(b.departs()).minusMinutes(bm.departureBufferMinutes()+bt.minutes());
        var home=time(b.arrives()).plusMinutes(bm.arrivalBufferMinutes()+bm.bostonTransferMinutes());
        var start=time(o.departs()).minusMinutes(om.departureBufferMinutes()+om.bostonTransferMinutes());
        List<Violation> violations=new ArrayList<>();
        if(ready.isAfter(time(r.hotelReadyBy()))) violations.add(new Violation("HOTEL_DEADLINE","Hotel ready at "+ready.toLocalTime()+", after your deadline."));
        if(leave.isBefore(time(r.leaveHotelNoEarlierThan()))) violations.add(new Violation("EARLY_RETURN_DEPARTURE","Must leave the hotel at "+leave.toLocalTime()+"."));
        if(home.isAfter(time(r.returnToOriginBy()))) violations.add(new Violation("LATE_ORIGIN_RETURN","Return to Boston at "+home.toLocalTime()+"."));
        if(total>r.budgetCents()) violations.add(new Violation("OVER_BUDGET","Total exceeds the confirmed budget."));
        return new CheckedTrip(o.id()+"~"+b.id()+"~"+h.id(),o.id(),b.id(),h.id(),h.name(),h.roomDescription(),h.quietRoom(),o.mode(),b.mode(),
            transport,lodging,transfer,total,ot.minutes()+bt.minutes()+om.bostonTransferMinutes()+bm.bostonTransferMinutes(),
            start.toString(),o.departs(),o.arrives(),arrival.toString(),ready.toString(),leave.toString(),b.departs(),b.arrives(),home.toString(),List.copyOf(violations));
    }
    public void requireCoverage(List<String> supplied, List<String> expected) {
        if(supplied==null || supplied.size()!=new HashSet<>(supplied).size() || !new HashSet<>(supplied).equals(new HashSet<>(expected)))
            throw ApiProblem.invalid("Candidate identifiers must cover the eligible catalog exactly, without duplicates. Expected " + expected + "; received " + supplied + ". Correct this array and retry evaluation; this is not trip infeasibility.");
    }
    public Proposal validateResult(String assessmentId, Snapshot snapshot, ModelResult result) {
        if(result==null || !assessmentId.equals(result.assessmentId()) || result.explanation()==null || result.explanation().isBlank())
            throw ApiProblem.invalid("Model result did not identify the correct assessment or explain its result.");
        validateProse(result.explanation());
        Evaluation evaluation=evaluate(snapshot);
        var feasible=evaluation.trips().stream().filter(t->t.violations().isEmpty()).toList();
        if(feasible.isEmpty()) {
            if(!"NO_FEASIBLE_TRIP".equals(result.status()) || result.recommended()!=null || result.alternative()!=null || evaluation.blockers().isEmpty()) throw ApiProblem.invalid("Model feasibility disagrees with the catalog.");
            return new Proposal("NO_FEASIBLE_TRIP",null,null,null,null,result.explanation(),evaluation.blockers(),evaluation.consideredCount(),0);
        }
        if(!"OPTIONS".equals(result.status()) || result.recommended()==null) throw ApiProblem.invalid("Model omitted a feasible recommendation.");
        CheckedTrip recommended=resolveChoice(feasible,result.recommended());
        if(preferenceOrder(snapshot.request()).compare(recommended,feasible.getFirst())>0) throw ApiProblem.invalid("Recommendation does not honor the saved preference order. Reassess to obtain a matching recommendation.");
        CheckedTrip alternative=result.alternative()==null?null:resolveChoice(feasible,result.alternative());
        if(alternative!=null && recommended.candidateId().equals(alternative.candidateId())) throw ApiProblem.invalid("Alternative must be distinct.");
        if(alternative!=null) {
            CheckedTrip other=alternative;
            if(!improvesPreference(snapshot.request(),other,recommended)) throw ApiProblem.invalid("An alternative must improve at least one saved preference; otherwise omit it.");
            if(other.totalCents()>=recommended.totalCents() && java.util.regex.Pattern.compile("(?i)less expensive|cheaper|save|saving|lower (?:price|cost)|more affordable").matcher(result.alternative().rationale()).find())
                throw ApiProblem.invalid("The alternative explanation contradicts its price.");
        }
        return new Proposal("OPTIONS",recommended,alternative,result.recommended().rationale(),result.alternative()==null?null:result.alternative().rationale(),result.explanation(),List.of(),evaluation.consideredCount(),feasible.size());
    }
    private CheckedTrip resolveChoice(List<CheckedTrip> feasible, Choice choice) {
        if(choice.rationale()==null || choice.rationale().isBlank()) throw ApiProblem.invalid("Choice must explain its tradeoff.");
        validateProse(choice.rationale());
        return feasible.stream().filter(t->t.candidateId().equals(choice.candidateId())).findFirst().orElseThrow(()->ApiProblem.invalid("Selected trip is unknown or infeasible."));
    }
    private void validateProse(String text) {
        if(java.util.regex.Pattern.compile("[\\p{N}\\p{Sc}]|QUIET_ROOM|SHORT_TRANSFERS|LOWEST_TOTAL").matcher(text).find())
            throw ApiProblem.invalid("Model explanation must be qualitative; the application owns numeric quote facts and comparisons.");
    }
}
