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
    @Autowired TripCalculator calculator;
    @Autowired ObjectMapper json;
    @Autowired org.springframework.jdbc.core.JdbcTemplate db;
    @BeforeEach void resetFixtures() {
        for(String table:List.of("booking","catalog_receipt","assessment","trip_revision","trip","hotel_night","hotel","travel_service","travel_rules")) db.update("delete from "+table);
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
}
