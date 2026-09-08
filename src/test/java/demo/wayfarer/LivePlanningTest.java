package demo.wayfarer;

import ai.loomspan.api.SkillTemplate;
import ai.loomspan.api.SkillExecutionView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static demo.wayfarer.Contracts.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="WAYFARER_LIVE_TEST",matches="true")
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:wayfarer-live;DB_CLOSE_DELAY=-1","execution-trace.persistence=ALWAYS"})
class LivePlanningTest {
    @Autowired SkillTemplate skills;
    @Autowired TripStore store;
    @Autowired TravelSkills leaves;
    @Autowired TripCalculator calculator;
    @Autowired ObjectMapper json;
    @Autowired org.springframework.jdbc.core.JdbcTemplate db;
    @BeforeEach void resetFixtures() {
        for(String table:List.of("service_cancellation","booking_exchange","booking","catalog_receipt","assessment","trip_revision","trip","hotel_night","hotel","travel_service","travel_rules")) db.update("delete from "+table);
        db.update("update catalog_guard set version=0 where id=1");
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(new org.springframework.core.io.ClassPathResource("db/migration/V2__seed_inventory.sql")).execute(Objects.requireNonNull(db.getDataSource()));
    }

    @Test void nestedPlanningProducesBookableQuietTripAndBudgetAlternative() throws Exception {
        var t=store.create(TripCalculator.example());var a=store.begin(t.id(),1);store.markRunning(a.id());
        AtomicReference<SkillExecutionView> observation=new AtomicReference<>();
        var result=skills.invoke("planTrip",new PlanningInput(a.id(),t.request()),observation::set);
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/live-observation.json"),json.writeValueAsString(observation.get()));
        System.out.println("LIVE_RESULT="+result);
        var model=json.readValue(result,ModelResult.class);var proposal=calculator.validateResult(a.id(),store.snapshot(a.id()),model);
        assertNotNull(observation.get());assertEquals(98000,proposal.recommended().totalCents());assertNotNull(proposal.alternative());assertEquals(84000,proposal.alternative().totalCents());
        var events=observation.get().events();
        System.out.println("LIVE_SESSION="+observation.get().sessionId());
        events.stream().filter(e->e.type().equals("SKILL_STARTED")||e.type().equals("SKILL_FINISHED")).forEach(e->System.out.println("LIVE_FRAME="+e.timestamp()+" "+e.type()+" "+e.route()+" "+e.details()));
        assertTrue(events.stream().filter(e->e.type().equals("SKILL_STARTED")).count()>=8,"Expected root, three specialists and four Java leaf executions.");
        var transportStart=events.stream().filter(e->e.type().equals("SKILL_STARTED")&&"planTransport".equals(e.route())).findFirst().orElseThrow().timestamp();
        var transportEnd=events.stream().filter(e->e.type().equals("SKILL_FINISHED")&&"planTransport".equals(e.route())).findFirst().orElseThrow().timestamp();
        var stayStart=events.stream().filter(e->e.type().equals("SKILL_STARTED")&&"assessStay".equals(e.route())).findFirst().orElseThrow().timestamp();
        var stayEnd=events.stream().filter(e->e.type().equals("SKILL_FINISHED")&&"assessStay".equals(e.route())).findFirst().orElseThrow().timestamp();
        assertTrue(transportStart.isBefore(stayEnd)&&stayStart.isBefore(transportEnd),"Independent specialists should actually overlap.");
        store.complete(a.id(),model,observation.get().sessionId(),List.of());
        var booking=store.book(t.id(),new BookingCommand(a.id(),proposal.recommended().candidateId(),"live-accept-123"));
        assertEquals(98000,booking.quote().totalCents());assertEquals(booking.id(),store.get(t.id()).booking().id());
        var r=t.request();var revised=new TripRequest(r.origin(),r.destination(),r.outboundDate(),r.returnDate(),2,1,r.budgetCents(),r.hotelReadyBy(),r.leaveHotelNoEarlierThan(),"2026-10-18T21:30:00-04:00",r.allowedModes(),r.priorities());
        store.revise(t.id(),new RevisionCommand(1,revised));var change=store.begin(t.id(),2);store.markRunning(change.id());
        var changedResult=skills.invoke("planTrip",new PlanningInput(change.id(),revised),observation::set);
        store.complete(change.id(),json.readValue(changedResult,ModelResult.class),observation.get().sessionId(),List.of());
        assertNull(store.assessment(change.id()).result().alternative(),"No other trip improves a saved preference");
        var replacement=store.assessment(change.id()).result().recommended();
        assertEquals("GARDEN",replacement.hotelId());assertEquals("AIR-RETURN",replacement.returnServiceId());
        assertTrue(replacement.returnToOriginAt().contains("21:10"));assertEquals(105000,replacement.totalCents());
        assertEquals(booking,store.booking(t.id()),"Live planning must preserve the current booking");
        var accepted=store.exchange(t.id(),new BookingCommand(change.id(),replacement.candidateId(),"live-exchange-123"));
        assertEquals(105000,accepted.quote().totalCents());assertEquals(1,store.get(t.id()).changes().size());
        System.out.println("LIVE_EXCHANGE_SESSION="+observation.get().sessionId());

    }
    @Test void railOnlyUnderBudgetRequestReportsInfeasibilityWithoutFlightSearch() {
        var r=TripCalculator.example();r=new TripRequest(r.origin(),r.destination(),r.outboundDate(),r.returnDate(),2,1,80000,r.hotelReadyBy(),r.leaveHotelNoEarlierThan(),r.returnToOriginBy(),List.of("rail"),r.priorities());
        var t=store.create(r);var a=store.begin(t.id(),1);store.markRunning(a.id());
        AtomicReference<SkillExecutionView> observation=new AtomicReference<>();
        var result=skills.invoke("planTrip",new PlanningInput(a.id(),r),observation::set);
        var model=json.readValue(result,ModelResult.class);
        store.complete(a.id(),model,observation.get().sessionId(),List.of());
        var proposal=store.assessment(a.id()).result();
        assertEquals("NO_FEASIBLE_TRIP",proposal.status());assertNull(proposal.recommended());assertTrue(proposal.blockers().getFirst().message().contains("$40.00"));
        assertTrue(observation.get().events().stream().anyMatch(e->"searchRailServices".equals(e.route())));
        assertTrue(observation.get().events().stream().noneMatch(e->"searchFlightServices".equals(e.route())));
        System.out.println("LIVE_RAIL_ONLY_SESSION="+observation.get().sessionId());
    }
    @Test void canceledRailReturnNeedsModeConsentBeforeLiveRecovery() {
        var r=TripCalculator.example();r=new TripRequest(r.origin(),r.destination(),r.outboundDate(),r.returnDate(),2,1,r.budgetCents(),r.hotelReadyBy(),r.leaveHotelNoEarlierThan(),r.returnToOriginBy(),List.of("rail"),r.priorities());
        var t=store.create(r);var a=store.begin(t.id(),1);store.markRunning(a.id());
        leaves.searchRailServices(a.id());leaves.searchHotels(a.id());leaves.evaluateTripOptions(a.id());
        store.complete(a.id(),new ModelResult(a.id(),"OPTIONS",new Choice("RAIL-OUT~RAIL-RETURN~GARDEN","Quiet room."),null,"Fixture booking for a live recovery."),"fixture",List.of());
        var old=store.book(t.id(),new BookingCommand(a.id(),"RAIL-OUT~RAIL-RETURN~GARDEN","recovery-fixture-key"));
        store.cancelReturn(t.id(),new CancellationCommand(old.id(),old.quote().returnServiceId()));
        var blocked=store.begin(t.id(),1);store.markRunning(blocked.id());AtomicReference<SkillExecutionView> observed=new AtomicReference<>();
        var result=skills.invoke("planTrip",new PlanningInput(blocked.id(),r),observed::set);
        store.complete(blocked.id(),json.readValue(result,ModelResult.class),observed.get().sessionId(),List.of());
        assertEquals("NO_FEASIBLE_TRIP",store.assessment(blocked.id()).result().status());
        var consent=store.assessment(blocked.id()).recoverySuggestions().getFirst();assertEquals(List.of("rail","flight"),consent.request().allowedModes());
        assertEquals(old,store.booking(t.id()));store.revise(t.id(),new RevisionCommand(1,consent.request()));
        var recovery=store.begin(t.id(),2);store.markRunning(recovery.id());
        var recovered=skills.invoke("planTrip",new PlanningInput(recovery.id(),consent.request()),observed::set);
        store.complete(recovery.id(),json.readValue(recovered,ModelResult.class),observed.get().sessionId(),List.of());
        var quote=store.assessment(recovery.id()).result().recommended();assertEquals(105000,quote.totalCents());assertEquals("RAIL-OUT",quote.outboundServiceId());
        store.exchange(t.id(),new BookingCommand(recovery.id(),quote.candidateId(),"live-recovery-accept"));
        assertNull(store.get(t.id()).disruption());assertEquals(0,db.queryForObject("select seats from travel_service where id='RAIL-RETURN'",Integer.class));
        System.out.println("LIVE_RECOVERY_SESSION="+observed.get().sessionId());
    }

}
