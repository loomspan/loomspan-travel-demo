package demo.wayfarer;

import ai.loomspan.api.SkillTemplate;
import ai.loomspan.api.SkillExecutionView;
import ai.loomspan.api.SkillException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import static demo.wayfarer.Contracts.*;
import static demo.wayfarer.IntakeContracts.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:intake-test;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
class IntakeServiceTest {
    @Autowired IntakeService intake;
    @Autowired TripStore store;
    @Autowired JdbcTemplate db;
    @Autowired TravelSkills leaves;
    @MockitoBean SkillTemplate skills;
    @BeforeEach void resetData() {
        for(String table:List.of("intake_draft","service_cancellation","booking_exchange","booking","catalog_receipt","assessment","trip_revision","trip","hotel_night","hotel","travel_service","travel_rules"))db.update("delete from "+table);
        db.update("update catalog_guard set version=0 where id=1");
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(new org.springframework.core.io.ClassPathResource("db/migration/V2__seed_inventory.sql")).execute(Objects.requireNonNull(db.getDataSource()));
    }
    Patch budgetPatch() {return new Patch(null,10000L,null,null,null,null,null);}
    void returns(Result result) {
        when(skills.invoke(eq("interpretTripChange"),any(Object.class),any())).thenAnswer(call->{
            Consumer<SkillExecutionView> observer=call.getArgument(2);observer.accept(new SkillExecutionView("intake-test-session",List.of()));return store.encode(result);
        });
    }
    View budgetDraft(TripView trip) {returns(new Result("READY","Proposed saved-budget increase.",budgetPatch()));return intake.interpret(trip.id(),new Command("Another $100",null));}
    @Test void reviewedDeltaPreservesOtherFieldsAndConfirmationIsIdempotent() {
        var trip=store.create(TripCalculator.example());var draft=budgetDraft(trip);
        assertEquals(130000,draft.proposedRequest().budgetCents());assertEquals(trip.request(),store.get(trip.id()).request());
        assertEquals(List.of(new Difference("Total budget","$1200.00","$1300.00")),draft.differences());
        assertEquals(0,db.queryForObject("select count(*) from assessment",Integer.class));
        var confirmed=intake.confirm(trip.id(),draft.id());assertEquals(2,confirmed.revision());assertEquals(draft.proposedRequest(),confirmed.request());
        assertEquals(2,intake.confirm(trip.id(),draft.id()).revision());assertEquals(2,intake.latest(trip.id()).confirmedRevision());
        assertEquals("intake-test-session",intake.latest(trip.id()).sessionId());assertEquals(0,db.queryForObject("select count(*) from booking",Integer.class));
    }
    @Test void clarificationRetainsConversationAndRequiresConcreteProposal() {
        var trip=store.create(TripCalculator.example());returns(new Result("CLARIFY","What exact Boston return deadline should I use?",null));
        var question=intake.interpret(trip.id(),new Command("Get us home earlier",null));
        assertNull(question.proposedRequest());assertThrows(ApiProblem.class,()->intake.confirm(trip.id(),question.id()));
        when(skills.invoke(eq("interpretTripChange"),any(Object.class),any())).thenAnswer(call->{
            Input input=call.getArgument(1);assertEquals(3,input.conversation().size());assertEquals("traveler",input.conversation().getLast().role());
            return store.encode(new Result("READY","Proposed return deadline.",new Patch(null,null,null,null,"21:30",null,null)));
        });
        var proposal=intake.interpret(trip.id(),new Command("9:30 pm Eastern",question.id()));
        assertTrue(proposal.proposedRequest().returnToOriginBy().contains("21:30"));assertEquals(1,store.get(trip.id()).revision());
        assertEquals(4,intake.get(trip.id(),proposal.id()).conversation().size());
    }
    @Test void staleRequestAndCatalogRejectConfirmation() {
        var trip=store.create(TripCalculator.example());var draft=budgetDraft(trip);
        store.revise(trip.id(),new RevisionCommand(1,trip.request()));assertThrows(ApiProblem.class,()->intake.confirm(trip.id(),draft.id()));
        var fresh=budgetDraft(store.get(trip.id()));db.update("update catalog_guard set version=version+1 where id=1");
        assertThrows(ApiProblem.class,()->intake.confirm(trip.id(),fresh.id()));assertEquals(2,store.get(trip.id()).revision());
    }
    @Test void bookingCreatedAfterInterpretationInvalidatesDraft() {
        var trip=store.create(TripCalculator.example());var draft=budgetDraft(trip);var a=store.begin(trip.id(),1);store.markRunning(a.id());
        leaves.searchRailServices(a.id());leaves.searchFlightServices(a.id());leaves.searchHotels(a.id());leaves.evaluateTripOptions(a.id());
        store.complete(a.id(),new ModelResult(a.id(),"OPTIONS",new Choice("RAIL-OUT~RAIL-RETURN~GARDEN","Quiet room."),null,"A valid booking."),"fixture",List.of());
        var booked=store.book(trip.id(),new BookingCommand(a.id(),"RAIL-OUT~RAIL-RETURN~GARDEN","intake-booking-key"));
        assertThrows(ApiProblem.class,()->intake.confirm(trip.id(),draft.id()));assertEquals(booked,store.booking(trip.id()));
    }
    @Test void invalidPatchesUnsupportedRequestsAndProviderFailuresDoNotChangeTrip() {
        var trip=store.create(TripCalculator.example());
        returns(new Result("UNSUPPORTED","Changing destination is outside this demo.",null));var unsupported=intake.interpret(trip.id(),new Command("Go to Paris",null));
        assertThrows(ApiProblem.class,()->intake.confirm(trip.id(),unsupported.id()));
        for(Patch patch:List.of(new Patch(10000L,10000L,null,null,null,null,null),new Patch(null,null,null,null,"25:00",null,null),new Patch(-1L,null,null,null,null,null,null),new Patch(null,null,null,null,null,List.of("boat"),null),new Patch(null,Long.MAX_VALUE,null,null,null,null,null))) {
            returns(new Result("READY","Proposed edit.",patch));assertThrows(ApiProblem.class,()->intake.interpret(trip.id(),new Command("An invalid edit",null)));
        }
        when(skills.invoke(eq("interpretTripChange"),any(Object.class),any())).thenThrow(new SkillException("provider error"));
        assertThrows(ApiProblem.class,()->intake.interpret(trip.id(),new Command("Another $100",null)));
        assertEquals(trip.request(),store.get(trip.id()).request());assertEquals(1,db.queryForObject("select count(*) from intake_draft",Integer.class));
    }
    @Test void foreignAndStaleClarificationParentsAreRejected() {
        var a=store.create(TripCalculator.example());var b=store.create(TripCalculator.example());returns(new Result("CLARIFY","What time?",null));
        var question=intake.interpret(a.id(),new Command("Earlier",null));
        assertThrows(ApiProblem.class,()->intake.interpret(b.id(),new Command("9 pm",question.id())));
        assertThrows(ApiProblem.class,()->intake.confirm(b.id(),question.id()));
        store.revise(a.id(),new RevisionCommand(1,a.request()));assertThrows(ApiProblem.class,()->intake.interpret(a.id(),new Command("9 pm",question.id())));
    }
    @Test void simultaneousConfirmationsCreateExactlyOneRevision() throws Exception {
        var trip=store.create(TripCalculator.example());var draft=budgetDraft(trip);var gate=new CountDownLatch(1);
        try(var workers=Executors.newFixedThreadPool(2)) {
            var one=workers.submit(()->{gate.await();return intake.confirm(trip.id(),draft.id());});var two=workers.submit(()->{gate.await();return intake.confirm(trip.id(),draft.id());});gate.countDown();
            assertEquals(2,one.get(10,TimeUnit.SECONDS).revision());assertEquals(2,two.get(10,TimeUnit.SECONDS).revision());
        }
        assertEquals(2,db.queryForObject("select count(*) from trip_revision where trip_id=?",Integer.class,trip.id()));
    }
}
