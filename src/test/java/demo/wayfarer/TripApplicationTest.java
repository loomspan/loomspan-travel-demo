package demo.wayfarer;

import ai.loomspan.api.SkillTemplate;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static demo.wayfarer.Contracts.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:wayfarer-test;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000"})
class TripApplicationTest {
    @Autowired TripStore store;
    @Autowired TripCalculator calculator;
    @Autowired TravelSkills leaves;
    @Autowired JdbcTemplate db;
    @MockitoBean SkillTemplate skills;

    @BeforeEach void reset() {
        for(String table:List.of("intake_draft","service_cancellation","booking_exchange","booking","catalog_receipt","assessment","trip_revision","trip","hotel_night","hotel","travel_service","travel_rules")) db.update("delete from "+table);
        db.update("update catalog_guard set version=0 where id=1");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V2__seed_inventory.sql")).execute(Objects.requireNonNull(db.getDataSource()));
    }
    TripRequest budget(long cents) {var r=TripCalculator.example();return new TripRequest(r.origin(),r.destination(),r.outboundDate(),r.returnDate(),2,1,cents,r.hotelReadyBy(),r.leaveHotelNoEarlierThan(),r.returnToOriginBy(),r.allowedModes(),r.priorities());}
    AssessmentView begin(TripRequest request) {var t=store.create(request);return store.begin(t.id(),t.revision());}
    AssessmentView proposal(String hotel) {
        var r=TripCalculator.example();
        if(hotel.equals("CENTRAL")) r=new TripRequest(r.origin(),r.destination(),r.outboundDate(),r.returnDate(),2,1,r.budgetCents(),r.hotelReadyBy(),r.leaveHotelNoEarlierThan(),r.returnToOriginBy(),r.allowedModes(),List.of("LOWEST_TOTAL","QUIET_ROOM","SHORT_TRANSFERS"));
        var a=begin(r);store.markRunning(a.id());
        leaves.searchRailServices(a.id());leaves.searchFlightServices(a.id());leaves.searchHotels(a.id());leaves.evaluateTripOptions(a.id());
        store.complete(a.id(),new ModelResult(a.id(),"OPTIONS",new Choice("RAIL-OUT~RAIL-RETURN~"+hotel,"Chosen from valid fixture inventory."),null,"A checked trip."),"test-session",List.of());return store.assessment(a.id());
    }
    BookingCommand command(AssessmentView a,String hotel) {return new BookingCommand(a.id(),"RAIL-OUT~RAIL-RETURN~"+hotel,"test-key-123");}

    @Test void allFixtureCombinationsHaveCorrectPartyPricingAndTiming() {
        var a=begin(TripCalculator.example());var e=calculator.evaluate(store.snapshot(a.id()));
        assertEquals(18,e.consideredCount());assertEquals(12,e.trips().stream().filter(t->t.violations().isEmpty()).count());
        var byId=new HashMap<String,CheckedTrip>();e.trips().forEach(t->byId.put(t.candidateId(),t));
        var garden=byId.get("RAIL-OUT~RAIL-RETURN~GARDEN");
        assertEquals(98000,garden.totalCents());assertEquals(36000,garden.transportCents());assertEquals(58000,garden.lodgingCents());assertEquals(4000,garden.transferCents());
        assertTrue(garden.hotelArrivalAt().contains("14:30"));assertTrue(garden.hotelReadyAt().contains("15:00"));assertTrue(garden.leaveHotelAt().contains("17:25"));assertTrue(garden.returnToOriginAt().contains("22:15"));
        assertEquals(84000,byId.get("RAIL-OUT~RAIL-RETURN~CENTRAL").totalCents());assertEquals(86000,byId.get("RAIL-OUT~RAIL-RETURN~RIVERSIDE").totalCents());
        assertEquals(96000,byId.get("AIR-OUT~AIR-RETURN~CENTRAL").totalCents());assertEquals(91000,byId.get("RAIL-OUT~AIR-RETURN~CENTRAL").totalCents());
        assertEquals("HOTEL_DEADLINE",byId.get("AIR-LATE~AIR-RETURN~CENTRAL").violations().getFirst().code());
    }
    @Test void budgetFailureUsesCheapestOtherwiseFeasibleTrip() {
        var a=begin(budget(80000));var e=calculator.evaluate(store.snapshot(a.id()));
        assertTrue(e.trips().stream().noneMatch(t->t.violations().isEmpty()));assertTrue(e.blockers().getFirst().message().contains("$840.00, $40.00"));
        var result=new ModelResult(a.id(),"NO_FEASIBLE_TRIP",null,null,"Budget is too low.");
        assertEquals("NO_FEASIBLE_TRIP",calculator.validateResult(a.id(),store.snapshot(a.id()),result).status());
    }
    @Test void exactBudgetAndArrivalBoundariesAreInclusive() {
        var a=begin(budget(84000));var e=calculator.evaluate(store.snapshot(a.id()));assertEquals(1,e.trips().stream().filter(t->t.violations().isEmpty()).count());
        var r=budget(120000);r=new TripRequest(r.origin(),r.destination(),r.outboundDate(),r.returnDate(),2,1,r.budgetCents(),"2026-10-16T15:00:00-04:00",r.leaveHotelNoEarlierThan(),r.returnToOriginBy(),r.allowedModes(),r.priorities());
        assertEquals(4,calculator.evaluate(store.snapshot(begin(r).id())).trips().stream().filter(t->t.violations().isEmpty()).count());
    }
    @Test void railOnlyIsSelectiveAndCoverageRejectsOmissionsDuplicatesAndForeignIds() {
        var r=TripCalculator.example();r=new TripRequest(r.origin(),r.destination(),r.outboundDate(),r.returnDate(),2,1,r.budgetCents(),r.hotelReadyBy(),r.leaveHotelNoEarlierThan(),r.returnToOriginBy(),List.of("rail"),r.priorities());
        var a=begin(r);assertEquals(1,leaves.searchRailServices(a.id()).outbound().size());assertThrows(ApiProblem.class,()->leaves.searchFlightServices(a.id()));
        assertThrows(ApiProblem.class,()->leaves.evaluateTripOptions(a.id()));
        leaves.searchHotels(a.id());assertEquals(3,leaves.evaluateTripOptions(a.id()).evaluation().consideredCount());
        assertThrows(ApiProblem.class,()->calculator.requireCoverage(List.of("a","a"),List.of("a")));
        assertThrows(ApiProblem.class,()->calculator.requireCoverage(List.of("foreign"),List.of("a")));
    }
    @Test void missingOneHotelNightExcludesTheHotelAndSnapshotsAreImmutable() {
        var a=begin(TripCalculator.example());db.update("delete from hotel_night where hotel_id='GARDEN' and stay_date='2026-10-17'");
        assertEquals(3,leaves.searchHotels(a.id()).hotels().size());
        var b=begin(TripCalculator.example());assertEquals(2,leaves.searchHotels(b.id()).hotels().size());
    }
    @Test void invalidModelResultsCannotBecomeProposals() {
        var a=begin(TripCalculator.example());var snapshot=store.snapshot(a.id());
        assertThrows(ApiProblem.class,()->calculator.validateResult(a.id(),snapshot,new ModelResult("foreign","OPTIONS",new Choice("RAIL-OUT~RAIL-RETURN~GARDEN","reason"),null,"reason")));
        assertThrows(ApiProblem.class,()->calculator.validateResult(a.id(),snapshot,new ModelResult(a.id(),"NO_FEASIBLE_TRIP",null,null,"wrong")));
        assertThrows(ApiProblem.class,()->calculator.validateResult(a.id(),snapshot,new ModelResult(a.id(),"OPTIONS",new Choice("AIR-OUT~AIR-RETURN~GARDEN","Quiet room."),null,"A trip.")));
        var bad=new Choice("AIR-LATE~AIR-RETURN~CENTRAL","late");assertThrows(ApiProblem.class,()->calculator.validateResult(a.id(),snapshot,new ModelResult(a.id(),"OPTIONS",bad,null,"wrong")));
        var valid=new Choice("RAIL-OUT~RAIL-RETURN~GARDEN","quiet");assertThrows(ApiProblem.class,()->calculator.validateResult(a.id(),snapshot,new ModelResult(a.id(),"OPTIONS",valid,new Choice("AIR-OUT~AIR-RETURN~GARDEN","Different flights."),"A trip.")));assertThrows(ApiProblem.class,()->calculator.validateResult(a.id(),snapshot,new ModelResult(a.id(),"OPTIONS",valid,valid,"duplicate")));
        assertThrows(ApiProblem.class,()->calculator.validateResult(a.id(),snapshot,new ModelResult(a.id(),"OPTIONS",valid,null,"Save $1,400.")));
        assertThrows(ApiProblem.class,()->calculator.validateResult(a.id(),snapshot,new ModelResult(a.id(),"OPTIONS",null,null,"missing")));
    }
    @Test void bookingReservesBothLegsAndNightsAndRetriesAreIdempotent() {
        var a=proposal("GARDEN");var command=command(a,"GARDEN");var booking=store.book(a.tripId(),command);
        assertEquals(booking.id(),store.book(a.tripId(),command).id());assertEquals(booking,store.get(a.tripId()).booking());
        assertEquals(1,db.queryForObject("select count(*) from booking",Integer.class));
    }
    @Test void acceptedBookingConsumesFourFiniteRows() {
        var a=proposal("GARDEN");store.book(a.tripId(),command(a,"GARDEN"));
        assertEquals(0,db.queryForObject("select sum(seats) from travel_service where id in ('RAIL-OUT','RAIL-RETURN')",Integer.class));
        assertEquals(0,db.queryForObject("select sum(rooms) from hotel_night where hotel_id='GARDEN'",Integer.class));
        assertEquals(18,db.queryForObject("select sum(seats) from travel_service where id like 'AIR-%'",Integer.class));
        assertThrows(ApiProblem.class,()->store.begin(a.tripId(),1));
        assertThrows(ApiProblem.class,()->store.book(a.tripId(),new BookingCommand(a.id(),"RAIL-OUT~RAIL-RETURN~CENTRAL","test-key-123")));
    }
    @Test void racingBookingsNeverPartiallyReserve() throws Exception {
        var a=proposal("GARDEN");var b=proposal("CENTRAL");var gate=new CountDownLatch(1);
        try(var workers=Executors.newFixedThreadPool(2)) {
            var results=List.of(workers.submit(()->raceBook(a,"GARDEN",gate)),workers.submit(()->raceBook(b,"CENTRAL",gate)));gate.countDown();
            assertEquals(1,results.stream().mapToInt(f->{try{return f.get(10,TimeUnit.SECONDS)?1:0;}catch(Exception e){throw new AssertionError(e);}}).sum());
        }
        assertEquals(1,db.queryForObject("select count(*) from booking",Integer.class));
        assertEquals(0,db.queryForObject("select sum(seats) from travel_service where id like 'RAIL-%'",Integer.class));
        assertEquals(14,db.queryForObject("select sum(rooms) from hotel_night",Integer.class));
    }
    private boolean raceBook(AssessmentView a,String hotel,CountDownLatch gate) throws InterruptedException {
        gate.await();try{store.book(a.tripId(),command(a,hotel));return true;}catch(ApiProblem e){assertEquals(409,e.status);return false;}
    }
    @Test void changedPricesRejectBookingWithoutStockWrites() {
        var a=proposal("GARDEN");String payload=db.queryForObject("select payload from hotel where id='GARDEN'",String.class);
        db.update("update hotel set payload=? where id='GARDEN'",payload.replace("29000","30000"));
        assertThrows(ApiProblem.class,()->store.book(a.tripId(),command(a,"GARDEN")));
        assertEquals(4,db.queryForObject("select sum(seats) from travel_service where id like 'RAIL-%'",Integer.class));
        assertEquals(0,db.queryForObject("select count(*) from booking",Integer.class));
    }
    @Test void historicalAndForeignProposalsCannotBeBooked() {
        var a=proposal("GARDEN");store.revise(a.tripId(),new RevisionCommand(1,budget(90000)));
        assertThrows(ApiProblem.class,()->store.book(a.tripId(),command(a,"GARDEN")));
        var other=store.create(TripCalculator.example());assertThrows(ApiProblem.class,()->store.book(other.id(),command(a,"GARDEN")));
    }
    @Test void interruptedAssessmentsBecomeRetryableFailuresAndLateResultsAreFenced() {
        var a=begin(TripCalculator.example());assertEquals(a.id(),store.begin(a.tripId(),1).id());store.markRunning(a.id());store.recoverInterrupted();
        assertEquals("FAILED",store.assessment(a.id()).status());
        assertThrows(ApiProblem.class,()->store.complete(a.id(),new ModelResult(a.id(),"OPTIONS",new Choice("RAIL-OUT~RAIL-RETURN~GARDEN","reason"),null,"reason"),"session",List.of()));
        assertNotEquals(a.id(),store.begin(a.tripId(),1).id());
    }
    @Test void unsupportedRequestsFailBeforePersistence() {
        var r=TripCalculator.example();assertThrows(ApiProblem.class,()->store.create(new TripRequest("SEA","NYC",r.outboundDate(),r.returnDate(),2,1,100000,r.hotelReadyBy(),r.leaveHotelNoEarlierThan(),r.returnToOriginBy(),r.allowedModes(),r.priorities())));
        assertThrows(ApiProblem.class,()->store.create(budget(-1)));assertEquals(0,store.list().size());
    }
    @Test void allAllowedSearchesAndActualEvaluationAreRequired() {
        var a=begin(TripCalculator.example());store.markRunning(a.id());
        leaves.searchRailServices(a.id());leaves.searchHotels(a.id());
        assertThrows(ApiProblem.class,()->leaves.evaluateTripOptions(a.id()));
        var result=new ModelResult(a.id(),"OPTIONS",new Choice("RAIL-OUT~RAIL-RETURN~GARDEN","quiet"),null,"reason");
        assertThrows(ApiProblem.class,()->store.complete(a.id(),result,"session",List.of()));
        leaves.searchFlightServices(a.id());leaves.evaluateTripOptions(a.id());
        store.complete(a.id(),result,"session",List.of());assertEquals("SUCCEEDED",store.assessment(a.id()).status());
        assertThrows(ApiProblem.class,()->leaves.searchHotels(a.id()));
    }
    TripRequest earlier() {
        var r=TripCalculator.example();
        return new TripRequest(r.origin(),r.destination(),r.outboundDate(),r.returnDate(),2,1,r.budgetCents(),r.hotelReadyBy(),r.leaveHotelNoEarlierThan(),"2026-10-18T21:30:00-04:00",r.allowedModes(),r.priorities());
    }
    AssessmentView changeProposal(String tripId) {
        var t=store.get(tripId);store.revise(tripId,new RevisionCommand(t.revision(),earlier()));
        var a=store.begin(tripId,t.revision()+1);store.markRunning(a.id());
        leaves.searchRailServices(a.id());leaves.searchFlightServices(a.id());leaves.searchHotels(a.id());leaves.evaluateTripOptions(a.id());
        store.complete(a.id(),new ModelResult(a.id(),"OPTIONS",new Choice("RAIL-OUT~AIR-RETURN~GARDEN","Keep the quiet hotel and return earlier."),null,"A feasible replacement."),"change-session",List.of());
        return store.assessment(a.id());
    }
    BookingCommand changeCommand(AssessmentView a,String key) {return new BookingCommand(a.id(),"RAIL-OUT~AIR-RETURN~GARDEN",key);}
    @Test void changePlanningCreditsOnlyOwnedInventoryAndPreservesHotelWithoutReleasingStock() {
        var original=proposal("GARDEN");var booking=store.book(original.tripId(),command(original,"GARDEN"));
        var change=changeProposal(original.tripId());
        assertEquals(booking.id(),change.baseBookingId());
        assertEquals(List.of("GARDEN"),leavesForSnapshot(change).stream().map(HotelOption::id).toList());
        assertEquals(105000,change.result().recommended().totalCents());
        assertTrue(change.result().recommended().returnToOriginAt().contains("21:10"));
        assertEquals(booking,store.booking(original.tripId()));
        assertEquals(0,db.queryForObject("select sum(seats) from travel_service where id like 'RAIL-%'",Integer.class));
        var outsider=begin(TripCalculator.example());assertTrue(calculator.eligibleHotels(store.snapshot(outsider.id())).stream().noneMatch(h->h.id().equals("GARDEN")));
        assertTrue(calculator.eligibleServices(store.snapshot(outsider.id()),"outbound").stream().noneMatch(s->s.id().equals("RAIL-OUT")));
        assertThrows(ApiProblem.class,()->store.book(original.tripId(),changeCommand(change,"wrong-endpoint")));
    }
    List<HotelOption> leavesForSnapshot(AssessmentView a) {return calculator.eligibleHotels(store.snapshot(a.id()));}
    @Test void exchangeAtomicallyReplacesInventoryAndPersistsHistoryWithIdempotentReplay() {
        var a=proposal("GARDEN");var old=store.book(a.tripId(),command(a,"GARDEN"));var change=changeProposal(a.tripId());var cmd=changeCommand(change,"exchange-key-one");
        var accepted=store.exchange(a.tripId(),cmd);
        assertNotEquals(old.id(),accepted.id());assertEquals(accepted,store.exchange(a.tripId(),cmd));
        assertEquals(0,db.queryForObject("select seats from travel_service where id='RAIL-OUT'",Integer.class));
        assertEquals(2,db.queryForObject("select seats from travel_service where id='RAIL-RETURN'",Integer.class));
        assertEquals(4,db.queryForObject("select seats from travel_service where id='AIR-RETURN'",Integer.class));
        assertEquals(0,db.queryForObject("select sum(rooms) from hotel_night where hotel_id='GARDEN'",Integer.class));
        assertEquals(List.of(new BookingChange(old,accepted)),store.get(a.tripId()).changes());
        assertThrows(ApiProblem.class,()->store.exchange(a.tripId(),changeCommand(change,"another-key")));
        assertThrows(ApiProblem.class,()->store.exchange(a.tripId(),new BookingCommand(a.id(),a.result().recommended().candidateId(),cmd.idempotencyKey())));
    }
    @Test void failedExchangeRollsBackReleasedInventoryAndRetainsOriginalBooking() {
        var a=proposal("GARDEN");var old=store.book(a.tripId(),command(a,"GARDEN"));var change=changeProposal(a.tripId());
        db.update("update travel_service set seats=0 where id='AIR-RETURN'");
        assertThrows(ApiProblem.class,()->store.exchange(a.tripId(),changeCommand(change,"sold-out-key")));
        assertEquals(old,store.booking(a.tripId()));assertTrue(store.get(a.tripId()).changes().isEmpty());
        assertEquals(0,db.queryForObject("select sum(seats) from travel_service where id like 'RAIL-%'",Integer.class));
        assertEquals(0,db.queryForObject("select sum(rooms) from hotel_night where hotel_id='GARDEN'",Integer.class));
        db.update("update travel_service set seats=6 where id='AIR-RETURN'");
        var payload=db.queryForObject("select payload from hotel where id='GARDEN'",String.class);
        db.update("update hotel set payload=? where id='GARDEN'",payload.replace("29000","30000"));
        assertThrows(ApiProblem.class,()->store.exchange(a.tripId(),changeCommand(change,"changed-price-key")));
        assertEquals(old,store.booking(a.tripId()));assertEquals(6,db.queryForObject("select seats from travel_service where id='AIR-RETURN'",Integer.class));
        assertEquals(0,db.queryForObject("select sum(rooms) from hotel_night where hotel_id='GARDEN'",Integer.class));
    }
    @Test void staleAndForeignExchangeProposalsCannotReplaceBooking() {
        var a=proposal("GARDEN");var old=store.book(a.tripId(),command(a,"GARDEN"));var change=changeProposal(a.tripId());
        var other=store.create(TripCalculator.example());
        assertThrows(ApiProblem.class,()->store.exchange(other.id(),changeCommand(change,"foreign-change-key")));
        store.revise(a.tripId(),new RevisionCommand(2,budget(90000)));
        assertThrows(ApiProblem.class,()->store.exchange(a.tripId(),changeCommand(change,"stale-change-key")));
        assertEquals(old,store.booking(a.tripId()));
    }
    @Test void concurrentExchangeAcceptancesHaveOneWinner() throws Exception {
        var a=proposal("GARDEN");store.book(a.tripId(),command(a,"GARDEN"));var change=changeProposal(a.tripId());var gate=new CountDownLatch(1);
        try(var workers=Executors.newFixedThreadPool(2)) {
            var results=new ArrayList<Future<Boolean>>();
            for(String key:List.of("concurrent-change-one","concurrent-change-two")) results.add(workers.submit(()->{
                gate.await();try{store.exchange(a.tripId(),changeCommand(change,key));return true;}catch(ApiProblem e){assertEquals(409,e.status);return false;}
            }));
            gate.countDown();int wins=0;for(var result:results) if(result.get(10,TimeUnit.SECONDS)) wins++;
            assertEquals(1,wins);
        }
        assertEquals(1,store.get(a.tripId()).changes().size());
        assertEquals(4,db.queryForObject("select seats from travel_service where id='AIR-RETURN'",Integer.class));
        assertEquals(2,db.queryForObject("select seats from travel_service where id='RAIL-RETURN'",Integer.class));
    }

    @Test void exchangeAndNewBookingCompetingForLastSeatsHaveOneWinner() throws Exception {
        var a=proposal("GARDEN");var original=store.book(a.tripId(),command(a,"GARDEN"));
        db.update("update travel_service set seats=2 where id='AIR-RETURN'");
        var change=changeProposal(a.tripId());var outsider=begin(TripCalculator.example());store.markRunning(outsider.id());
        leaves.searchRailServices(outsider.id());leaves.searchFlightServices(outsider.id());leaves.searchHotels(outsider.id());leaves.evaluateTripOptions(outsider.id());
        store.complete(outsider.id(),new ModelResult(outsider.id(),"OPTIONS",new Choice("AIR-OUT~AIR-RETURN~CENTRAL","Available flights."),null,"A valid trip."),"outside-session",List.of());
        var gate=new CountDownLatch(1);
        try(var workers=Executors.newFixedThreadPool(2)) {
            var exchange=workers.submit(()->{gate.await();try{store.exchange(a.tripId(),changeCommand(change,"last-seat-exchange"));return true;}catch(ApiProblem e){return false;}});
            var booking=workers.submit(()->{gate.await();try{store.book(outsider.tripId(),new BookingCommand(outsider.id(),"AIR-OUT~AIR-RETURN~CENTRAL","last-seat-booking"));return true;}catch(ApiProblem e){return false;}});
            gate.countDown();boolean exchanged=exchange.get(10,TimeUnit.SECONDS);assertNotEquals(exchanged,booking.get(10,TimeUnit.SECONDS));
            assertEquals(exchanged?2:0,db.queryForObject("select seats from travel_service where id='RAIL-RETURN'",Integer.class));
            if(!exchanged) assertEquals(original,store.booking(a.tripId()));
        }
        assertEquals(0,db.queryForObject("select seats from travel_service where id='AIR-RETURN'",Integer.class));
        assertEquals(0,db.queryForObject("select sum(rooms) from hotel_night where hotel_id='GARDEN'",Integer.class));
    }
    @Test void infeasibleChangeAndMissingHeldNightNeverReleaseCurrentBooking() {
        var a=proposal("GARDEN");var original=store.book(a.tripId(),command(a,"GARDEN"));
        store.revise(a.tripId(),new RevisionCommand(1,budget(80000)));var change=store.begin(a.tripId(),2);
        assertTrue(calculator.evaluate(store.snapshot(change.id())).trips().stream().noneMatch(q->q.violations().isEmpty()));
        assertEquals(original,store.booking(a.tripId()));
        db.update("delete from hotel_night where hotel_id='GARDEN' and stay_date='2026-10-17'");
        store.revise(a.tripId(),new RevisionCommand(2,earlier()));var missing=store.begin(a.tripId(),3);
        assertTrue(calculator.eligibleHotels(store.snapshot(missing.id())).isEmpty());
        assertEquals(original,store.booking(a.tripId()));
    }

    CancellationCommand cancelCommand(BookingView b) {return new CancellationCommand(b.id(),b.quote().returnServiceId());}
    AssessmentView recovery(String tripId) {
        var t=store.get(tripId);var a=store.begin(tripId,t.revision());store.markRunning(a.id());
        for(String mode:t.request().allowedModes()) {if(mode.equals("rail")) leaves.searchRailServices(a.id());else leaves.searchFlightServices(a.id());}
        leaves.searchHotels(a.id());var e=leaves.evaluateTripOptions(a.id());
        var choice=e.selections().recommendedCandidateId();
        store.complete(a.id(),new ModelResult(a.id(),choice==null?"NO_FEASIBLE_TRIP":"OPTIONS",choice==null?null:new Choice(choice,"Retain the hotel and outbound journey."),null,"Remaining inventory was evaluated."),"recovery-session",List.of());
        return store.assessment(a.id());
    }
    @Test void cancellationIsIdempotentAndRecoveryRetainsUnaffectedReservations() {
        var a=proposal("GARDEN");var original=store.book(a.tripId(),command(a,"GARDEN"));
        var canceled=store.cancelReturn(a.tripId(),cancelCommand(original));
        assertEquals("RAIL-RETURN",canceled.disruption().serviceId());assertTrue(store.list().getFirst().needsAttention());
        assertEquals(1,canceled.catalogVersion());assertEquals(1,store.cancelReturn(a.tripId(),cancelCommand(original)).catalogVersion());
        assertEquals(original,store.booking(a.tripId()));
        var recovered=recovery(a.tripId());assertEquals("RAIL-RETURN",recovered.recoveryServiceId());
        assertEquals(1,calculator.eligibleServices(store.snapshot(recovered.id()),"outbound").size());
        assertTrue(store.snapshot(recovered.id()).services().stream().noneMatch(service->service.id().equals("RAIL-RETURN")));
        assertEquals(105000,recovered.result().recommended().totalCents());
        var accepted=store.exchange(a.tripId(),new BookingCommand(recovered.id(),recovered.result().recommended().candidateId(),"recovery-accept-key"));
        assertNull(store.get(a.tripId()).disruption());assertFalse(store.list().getFirst().needsAttention());
        assertEquals("RAIL-OUT",accepted.quote().outboundServiceId());assertEquals("GARDEN",accepted.quote().hotelId());
        assertEquals(0,db.queryForObject("select seats from travel_service where id='RAIL-RETURN'",Integer.class));
        assertEquals(0,db.queryForObject("select seats from travel_service where id='RAIL-OUT'",Integer.class));
        assertEquals(0,db.queryForObject("select sum(rooms) from hotel_night where hotel_id='GARDEN'",Integer.class));
        assertThrows(ApiProblem.class,()->store.cancelReturn(a.tripId(),cancelCommand(original)));
    }
    @Test void cancellationAffectsAllBookingsAndFencesEarlierProposals() {
        db.update("update travel_service set seats=4 where id like 'RAIL-%'");
        var a=proposal("GARDEN");var b=proposal("CENTRAL");var old=store.book(a.tripId(),command(a,"GARDEN"));
        var second=store.book(b.tripId(),command(b,"CENTRAL"));
        var pending=changeProposal(a.tripId());
        store.cancelReturn(b.tripId(),cancelCommand(second));
        assertNotNull(store.get(a.tripId()).disruption());assertNotNull(store.get(b.tripId()).disruption());
        assertEquals(2,store.list().stream().filter(TripSummary::needsAttention).count());
        assertThrows(ApiProblem.class,()->store.exchange(a.tripId(),changeCommand(pending,"old-catalog-key")));
        assertEquals(old,store.booking(a.tripId()));
        assertEquals(0,db.queryForObject("select seats from travel_service where id='RAIL-RETURN'",Integer.class));
    }
    @Test void railOnlyRecoverySuggestsExplicitModeAndBudgetChangesWithoutApplyingThem() {
        var a=proposal("GARDEN");var old=store.book(a.tripId(),command(a,"GARDEN"));var r=budget(98000);
        r=new TripRequest(r.origin(),r.destination(),r.outboundDate(),r.returnDate(),2,1,r.budgetCents(),r.hotelReadyBy(),r.leaveHotelNoEarlierThan(),r.returnToOriginBy(),List.of("rail"),r.priorities());
        store.revise(a.tripId(),new RevisionCommand(1,r));store.cancelReturn(a.tripId(),cancelCommand(old));
        var result=recovery(a.tripId());assertEquals("NO_FEASIBLE_TRIP",result.result().status());
        var suggestion=result.recoverySuggestions().getFirst();
        assertEquals(List.of("rail","flight"),suggestion.request().allowedModes());assertEquals(105000,suggestion.request().budgetCents());
        assertEquals(2,suggestion.changes().size());assertEquals(r,store.get(a.tripId()).request());assertEquals(old,store.booking(a.tripId()));
        store.revise(a.tripId(),new RevisionCommand(2,suggestion.request()));var next=recovery(a.tripId());
        assertEquals(105000,next.result().recommended().totalCents());
    }
    @Test void failedRecoveryDoesNotReleaseHotelOrOutboundAndNoInventoryCannotBeFixedByPreferences() {
        var a=proposal("GARDEN");var old=store.book(a.tripId(),command(a,"GARDEN"));store.cancelReturn(a.tripId(),cancelCommand(old));var assessed=recovery(a.tripId());
        db.update("update travel_service set seats=0 where id='AIR-RETURN'");
        assertThrows(ApiProblem.class,()->store.exchange(a.tripId(),new BookingCommand(assessed.id(),assessed.result().recommended().candidateId(),"failed-recovery-key")));
        assertEquals(old,store.booking(a.tripId()));assertNotNull(store.get(a.tripId()).disruption());
        assertEquals(0,db.queryForObject("select seats from travel_service where id='RAIL-OUT'",Integer.class));
        assertEquals(0,db.queryForObject("select sum(rooms) from hotel_night where hotel_id='GARDEN'",Integer.class));
        var unavailable=recovery(a.tripId());assertEquals("NO_FEASIBLE_TRIP",unavailable.result().status());assertTrue(unavailable.recoverySuggestions().isEmpty());
    }
    @Test void cancellationDuringExchangeCannotLeaveAHealthyBookingOnCanceledService() throws Exception {
        var a=proposal("GARDEN");var old=store.book(a.tripId(),command(a,"GARDEN"));var change=changeProposal(a.tripId());var gate=new CountDownLatch(1);
        try(var workers=Executors.newFixedThreadPool(2)) {
            var cancel=workers.submit(()->{gate.await();try{store.cancelReturn(a.tripId(),cancelCommand(old));return true;}catch(ApiProblem e){return false;}});
            var exchange=workers.submit(()->{gate.await();try{store.exchange(a.tripId(),changeCommand(change,"racing-recovery-key"));return true;}catch(ApiProblem e){return false;}});
            gate.countDown();boolean canceled=cancel.get(10,TimeUnit.SECONDS);assertNotEquals(canceled,exchange.get(10,TimeUnit.SECONDS));
            assertEquals(canceled,store.get(a.tripId()).disruption()!=null);
        }
    }

}
