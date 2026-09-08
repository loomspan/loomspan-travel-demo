package demo.wayfarer;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import static demo.wayfarer.Contracts.*;

@Service
public class TripStore {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final TripCalculator calculator;
    public TripStore(JdbcTemplate db,ObjectMapper json,TripCalculator calculator) { this.db=db; this.json=json; this.calculator=calculator; }
    String encode(Object value) { return json.writeValueAsString(value); }
    <T> T decode(String value,Class<T> type) { return value==null?null:json.readValue(value,type); }
    static String id() { return UUID.randomUUID().toString(); }
    static String now() { return Instant.now().toString(); }
    private String scalar(String sql,Object...args) {
        var rows=db.query(sql,(rs,n)->rs.getString(1),args);
        if(rows.isEmpty()) throw ApiProblem.missing();
        return rows.getFirst();
    }
    private void lockTrip(String tripId) { scalar("select id from trip where id=? for update",tripId); }
    private void lockCatalog() {
        scalar("select id from catalog_guard where id=1 for update");
        db.queryForList("select id from travel_service order by id for update");
        db.queryForList("select id from hotel order by id for update");
        db.queryForList("select hotel_id,stay_date from hotel_night order by hotel_id,stay_date for update");
        db.queryForList("select id from travel_rules order by id for update");
    }
    @Transactional
    public TripView create(TripRequest request) {
        calculator.validate(request);
        String id=id(); db.update("insert into trip values (?,1,?)",id,now());
        db.update("insert into trip_revision values (?,1,?)",id,encode(request));
        return get(id);
    }
    @Transactional
    public TripView revise(String tripId,RevisionCommand command) {
        if(command==null) throw ApiProblem.invalid("Revision is required.");
        calculator.validate(command.request()); lockTrip(tripId);
        int revision=Integer.parseInt(scalar("select revision from trip where id=?",tripId));
        if(revision!=command.revision()) throw ApiProblem.conflict("The request changed. Reload before editing.");
        db.update("update trip set revision=? where id=?",revision+1,tripId);
        db.update("insert into trip_revision values (?,?,?)",tripId,revision+1,encode(command.request()));
        return get(tripId);
    }
    public List<TripSummary> list() {
        return db.query("select t.id,t.revision,t.created_at,exists(select 1 from booking b where b.trip_id=t.id) booked from trip t order by t.created_at desc",
            (rs,n)->new TripSummary(rs.getString("id"),rs.getInt("revision"),rs.getBoolean("booked"),rs.getString("created_at"),disruption(booking(rs.getString("id")))!=null));
    }
    public TripView get(String tripId) {
        int revision=Integer.parseInt(scalar("select revision from trip where id=?",tripId));
        TripRequest request=decode(scalar("select request_json from trip_revision where trip_id=? and revision=?",tripId,revision),TripRequest.class);
        var assessments=db.query("select id from assessment where trip_id=? order by created_at desc",(rs,n)->assessment(rs.getString(1)),tripId);
        var changes=db.query("select before_json,after_json from booking_exchange where trip_id=?",(rs,n)->new BookingChange(decode(rs.getString(1),BookingView.class),decode(rs.getString(2),BookingView.class)),tripId);
        changes.sort(Comparator.comparing(c->c.after().createdAt()));
        return new TripView(tripId,revision,request,assessments,booking(tripId),scalar("select created_at from trip where id=?",tripId),changes,disruption(booking(tripId)),catalogVersion());
    }

    public int catalogVersion() {return Integer.parseInt(scalar("select version from catalog_guard where id=1"));}
    private boolean cancelled(String serviceId) {return db.queryForObject("select count(*) from service_cancellation where service_id=?",Integer.class,serviceId)>0;}
    public String recoveryService(String assessmentId) {return scalar("select recovery_service_id from assessment where id=?",assessmentId);}
    public Cancellation disruption(BookingView held) {
        if(held==null) return null;
        var rows=db.query("select service_id,created_at from service_cancellation where service_id=?",(rs,n)->new Cancellation(rs.getString(1),rs.getString(2)),held.quote().returnServiceId());
        return rows.isEmpty()?null:rows.getFirst();
    }
    @Transactional
    public TripView cancelReturn(String tripId,CancellationCommand command) {
        if(command==null || command.bookingId()==null || command.serviceId()==null) throw ApiProblem.invalid("Identify the current booking and its return service.");
        lockTrip(tripId);var held=booking(tripId);
        if(held==null || !held.id().equals(command.bookingId()) || !held.quote().returnServiceId().equals(command.serviceId())) throw ApiProblem.conflict("The booking changed. Reload before simulating a cancellation.");
        lockCatalog();
        if(!cancelled(command.serviceId())) {
            db.update("insert into service_cancellation values (?,?)",command.serviceId(),now());
            db.update("update travel_service set seats=0 where id=?",command.serviceId());
            db.update("update catalog_guard set version=version+1 where id=1");
        }
        return get(tripId);
    }
    public Snapshot snapshot(String assessmentId) { return decode(scalar("select snapshot_json from assessment where id=?",assessmentId),Snapshot.class); }
    @Transactional
    public void recordReceipt(String assessmentId,String kind,Object value) {
        String status=scalar("select status from assessment where id=? for update",assessmentId);
        if(!List.of("QUEUED","RUNNING").contains(status)) throw ApiProblem.conflict("Assessment is no longer accepting catalog results.");
        String payload=encode(value);
        var existing=db.query("select payload from catalog_receipt where assessment_id=? and kind=?",(rs,n)->rs.getString(1),assessmentId,kind);
        if(existing.isEmpty()) db.update("insert into catalog_receipt values (?,?,?)",assessmentId,kind,payload);
        else if(!existing.getFirst().equals(payload)) throw ApiProblem.conflict("Immutable catalog result changed unexpectedly.");
    }
    public <T> T receipt(String assessmentId,String kind,Class<T> type) {
        var rows=db.query("select payload from catalog_receipt where assessment_id=? and kind=?",(rs,n)->rs.getString(1),assessmentId,kind);
        if(rows.isEmpty()) throw ApiProblem.conflict("Missing "+kind+" catalog result. Complete the corresponding specialist search before logistics; this is not trip infeasibility.");
        return decode(rows.getFirst(),type);
    }
    Snapshot capture(TripRequest request) {
        var services=db.query("select payload,seats from travel_service where id not in (select service_id from service_cancellation) order by id",(rs,n)->{
            var s=decode(rs.getString(1),ServiceOption.class);
            return new ServiceOption(s.id(),s.mode(),s.direction(),s.departs(),s.arrives(),s.farePerPersonCents(),rs.getInt(2));
        });
        var hotels=db.query("select payload from hotel order by id",(rs,n)->{
            var h=decode(rs.getString(1),HotelOption.class);
            var nights=db.queryForList("select rooms from hotel_night where hotel_id=? and stay_date>=? and stay_date<? order by stay_date",Integer.class,h.id(),request.outboundDate(),request.returnDate());
            int stock=nights.size()==2?nights.stream().mapToInt(Integer::intValue).min().orElse(0):0;
            return new HotelOption(h.id(),h.name(),h.nightlyRoomCents(),h.roomCapacity(),stock,h.checkIn(),h.quietRoom(),h.roomDescription(),h.transfers());
        });
        Map<String,ModeRules> modes=json.readValue(scalar("select payload from travel_rules where id=1"),json.getTypeFactory().constructMapType(Map.class,String.class,ModeRules.class));
        return new Snapshot(request,services,hotels,modes);
    }
    @Transactional
    public AssessmentView begin(String tripId,int revision) {
        lockTrip(tripId);lockCatalog();
        var held=booking(tripId);
        if(held!=null && disruption(held)==null && assessment(held.assessmentId()).revision()==revision) throw ApiProblem.conflict("Save a revised request before assessing a booking change.");
        if(Integer.parseInt(scalar("select revision from trip where id=?",tripId))!=revision) throw ApiProblem.conflict("Reload the current request before assessing.");
        var running=db.query("select id from assessment where trip_id=? and revision=? and status in ('QUEUED','RUNNING') and catalog_version=?",(rs,n)->rs.getString(1),tripId,revision,catalogVersion());
        if(!running.isEmpty()) return assessment(running.getFirst());
        TripRequest request=decode(scalar("select request_json from trip_revision where trip_id=? and revision=?",tripId,revision),TripRequest.class);
        Snapshot snapshot=exchangeSnapshot(capture(request),held); String id=id();
        db.update("insert into assessment (id,trip_id,revision,status,snapshot_json,events_json,created_at,base_booking_id,catalog_version,recovery_service_id) values (?,?,?,'QUEUED',?,'[]',?,?,?,?)",id,tripId,revision,encode(snapshot),now(),held==null?null:held.id(),catalogVersion(),disruption(held)==null?null:held.quote().returnServiceId());
        return assessment(id);
    }
    // Only this trip's held inventory is available for its replacement. No stock is
    // released while planning; the hotel is a hard constraint of this change flow.
    private Snapshot exchangeSnapshot(Snapshot snapshot,BookingView held) {
        if(held==null) return snapshot;
        var q=held.quote();
        var services=snapshot.services().stream().filter(s->disruption(held)==null || s.direction().equals("return") || s.id().equals(q.outboundServiceId())).map(s->new ServiceOption(s.id(),s.mode(),s.direction(),s.departs(),s.arrives(),s.farePerPersonCents(),
            s.seats()+(s.id().equals(q.outboundServiceId())||s.id().equals(q.returnServiceId())?2:0))).toList();
        var hotels=snapshot.hotels().stream().filter(h->h.id().equals(q.hotelId()) && db.queryForObject("select count(*) from hotel_night where hotel_id=? and stay_date>=? and stay_date<?",Integer.class,h.id(),snapshot.request().outboundDate(),snapshot.request().returnDate())==2)
            .map(h->new HotelOption(h.id(),h.name(),h.nightlyRoomCents(),h.roomCapacity(),h.roomsPerNight()+1,h.checkIn(),h.quietRoom(),h.roomDescription(),h.transfers())).toList();
        return new Snapshot(snapshot.request(),services,hotels,snapshot.modes());
    }
    public AssessmentView assessment(String id) {
        var rows=db.query("select * from assessment where id=?",(rs,n)->{
            List<EventSummary> events=json.readValue(rs.getString("events_json"),json.getTypeFactory().constructCollectionType(List.class,EventSummary.class));
            return new AssessmentView(rs.getString("id"),rs.getString("trip_id"),rs.getInt("revision"),rs.getString("status"),
                decode(rs.getString("result_json"),Proposal.class),rs.getString("error_message"),rs.getString("session_id"),events,rs.getString("created_at"),rs.getString("base_booking_id"),rs.getInt("catalog_version"),rs.getString("recovery_service_id"),
                "SUCCEEDED".equals(rs.getString("status")) && rs.getString("recovery_service_id")!=null && "NO_FEASIBLE_TRIP".equals(decode(rs.getString("result_json"),Proposal.class).status())?calculator.recoverySuggestions(decode(rs.getString("snapshot_json"),Snapshot.class)):List.of());
        },id);
        if(rows.isEmpty()) throw ApiProblem.missing(); return rows.getFirst();
    }
    public boolean markRunning(String id) { return db.update("update assessment set status='RUNNING' where id=? and status='QUEUED'",id)==1; }
    @Transactional
    public void complete(String id,ModelResult result,String session,List<EventSummary> events) {
        if(!receipt(id,"EVALUATION",Evaluation.class).equals(calculator.evaluate(snapshot(id)))) throw ApiProblem.invalid("Completed evaluation does not match the saved catalog.");
        Proposal proposal=calculator.validateResult(id,snapshot(id),result);
        if(db.update("update assessment set status='SUCCEEDED',result_json=?,session_id=?,events_json=? where id=? and status='RUNNING'",encode(proposal),session,encode(events),id)!=1)
            throw ApiProblem.conflict("Assessment is no longer running.");
    }
    public void fail(String id,String message,String session) {
        db.update("update assessment set status='FAILED',error_message=?,session_id=? where id=? and status in ('QUEUED','RUNNING')",message,session,id);
    }
    public void recoverInterrupted() {
        db.update("update assessment set status='FAILED',error_message='Application restarted before this assessment completed. Retry to start a new execution.' where status in ('QUEUED','RUNNING')");
    }
    public BookingView booking(String tripId) {
        var rows=db.query("select * from booking where trip_id=?",(rs,n)->new BookingView(rs.getString("id"),rs.getString("trip_id"),rs.getString("assessment_id"),rs.getString("candidate_id"),decode(rs.getString("quote_json"),CheckedTrip.class),rs.getString("created_at")),tripId);
        return rows.isEmpty()?null:rows.getFirst();
    }
    @Transactional
    public BookingView book(String tripId,BookingCommand command) {
        return accept(tripId,command,false);
    }
    @Transactional
    public BookingView exchange(String tripId,BookingCommand command) {
        return accept(tripId,command,true);
    }
    private BookingView accept(String tripId,BookingCommand command,boolean exchange) {
        if(command==null || command.assessmentId()==null || command.candidateId()==null || command.idempotencyKey()==null || !command.idempotencyKey().matches("[A-Za-z0-9_-]{8,80}")) throw ApiProblem.invalid("A proposal choice and valid idempotency key are required.");
        lockTrip(tripId);
        BookingView existing=booking(tripId);
        if(exchange) {
            var previous=db.query("select after_json from booking_exchange where trip_id=? and idempotency_key=?",(rs,n)->decode(rs.getString(1),BookingView.class),tripId,command.idempotencyKey());
            if(!previous.isEmpty()) {
                var accepted=previous.getFirst();
                if(accepted.assessmentId().equals(command.assessmentId()) && accepted.candidateId().equals(command.candidateId())) return accepted;
                throw ApiProblem.conflict("This acceptance key was used for a different change.");
            }
            if(existing==null) throw ApiProblem.conflict("A current booking is required for an exchange.");
            if(command.idempotencyKey().equals(scalar("select idempotency_key from booking where trip_id=?",tripId))) throw ApiProblem.conflict("Use a new acceptance key for a booking change.");
        } else if(existing!=null) {
            String key=scalar("select idempotency_key from booking where trip_id=?",tripId);
            if(key.equals(command.idempotencyKey()) && existing.assessmentId().equals(command.assessmentId()) && existing.candidateId().equals(command.candidateId())) return existing;
            throw ApiProblem.conflict("This trip already has a booking; the key or selected trip differs.");
        }
        var assessment=assessment(command.assessmentId());
        String baseline=scalar("select base_booking_id from assessment where id=?",assessment.id());
        if(exchange ? !existing.id().equals(baseline) : baseline!=null) throw ApiProblem.conflict("The booking changed since this assessment. Assess again before accepting.");
        if(!tripId.equals(assessment.tripId()) || assessment.revision()!=Integer.parseInt(scalar("select revision from trip where id=?",tripId)) || !"SUCCEEDED".equals(assessment.status()) || assessment.result()==null)
            throw ApiProblem.conflict("Only a completed proposal for the current request can be booked.");
        var proposal=assessment.result();
        CheckedTrip quoted=java.util.stream.Stream.of(proposal.recommended(),proposal.alternative()).filter(Objects::nonNull)
            .filter(t->t.candidateId().equals(command.candidateId())).findFirst().orElseThrow(()->ApiProblem.invalid("This choice is not in the proposal."));
        lockCatalog();
        if(assessment.catalogVersion()!=catalogVersion()) throw ApiProblem.conflict("A service was canceled after this assessment. Assess the remaining inventory again.");
        if(exchange && disruption(existing)!=null && !existing.quote().outboundServiceId().equals(quoted.outboundServiceId())) throw ApiProblem.conflict("Recovery must retain the booked outbound service.");
        if(exchange && !existing.quote().hotelId().equals(quoted.hotelId())) throw ApiProblem.conflict("A booking change must preserve the current hotel.");
        if(exchange) adjustInventory(existing.quote(),1);
        var current=calculator.evaluate(capture(snapshot(assessment.id()).request()));
        CheckedTrip checked=current.trips().stream().filter(t->t.candidateId().equals(quoted.candidateId())).findFirst().orElseThrow(()->ApiProblem.conflict("Inventory sold out. Assess again for current options."));
        if(!checked.equals(quoted)) throw ApiProblem.conflict("The price, itinerary, or availability changed. Assess again before booking.");
        adjustInventory(quoted,-1);
        if(exchange) db.update("delete from booking where trip_id=?",tripId);
        db.update("insert into booking values (?,?,?,?,?,?,?)",id(),tripId,assessment.id(),quoted.candidateId(),command.idempotencyKey(),encode(quoted),now());
        var accepted=booking(tripId);
        if(exchange) db.update("insert into booking_exchange values (?,?,?,?)",tripId,command.idempotencyKey(),encode(existing),encode(accepted));
        return accepted;
    }
    private void adjustInventory(CheckedTrip quote,int direction) {
        int out=db.update("update travel_service set seats=seats+? where id=?",2*direction,quote.outboundServiceId());
        int back=direction>0 && cancelled(quote.returnServiceId())?1:db.update("update travel_service set seats=seats+? where id=?",2*direction,quote.returnServiceId());
        int nights=db.update("update hotel_night set rooms=rooms+? where hotel_id=? and stay_date>=? and stay_date<?",direction,quote.hotelId(),"2026-10-16","2026-10-18");
        if(out!=1 || back!=1 || nights!=2) throw ApiProblem.conflict("Both travel legs and hotel nights must be available.");
    }
}
