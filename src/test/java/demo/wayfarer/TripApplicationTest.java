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
        for(String table:List.of("booking","catalog_receipt","assessment","trip_revision","trip","hotel_night","hotel","travel_service","travel_rules")) db.update("delete from "+table);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V2__seed_inventory.sql")).execute(Objects.requireNonNull(db.getDataSource()));
    }
    TripRequest budget(long cents) {var r=TripCalculator.example();return new TripRequest(r.origin(),r.destination(),r.outboundDate(),r.returnDate(),2,1,cents,r.hotelReadyBy(),r.leaveHotelNoEarlierThan(),r.returnToOriginBy(),r.allowedModes(),r.priorities());}
    AssessmentView begin(TripRequest request) {var t=store.create(request);return store.begin(t.id(),t.revision());}
    AssessmentView proposal(String hotel) {
        var a=begin(TripCalculator.example());store.markRunning(a.id());
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
        var bad=new Choice("AIR-LATE~AIR-RETURN~CENTRAL","late");assertThrows(ApiProblem.class,()->calculator.validateResult(a.id(),snapshot,new ModelResult(a.id(),"OPTIONS",bad,null,"wrong")));
        var valid=new Choice("RAIL-OUT~RAIL-RETURN~GARDEN","quiet");assertThrows(ApiProblem.class,()->calculator.validateResult(a.id(),snapshot,new ModelResult(a.id(),"OPTIONS",valid,valid,"duplicate")));
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
        assertThrows(ApiProblem.class,()->store.revise(a.tripId(),new RevisionCommand(1,budget(90000))));
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
}
