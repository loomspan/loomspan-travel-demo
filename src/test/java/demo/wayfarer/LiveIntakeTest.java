package demo.wayfarer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static demo.wayfarer.IntakeContracts.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="WAYFARER_LIVE_TEST",matches="true")
@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:live-intake;DB_CLOSE_DELAY=-1")
class LiveIntakeTest {
    @Autowired IntakeService intake;
    @Autowired TripStore store;
    @Autowired TravelSkills leaves;
    @Test void liveInterpretationClarifiesTimeComputesBudgetAndDeclinesUnsupportedChange() {
        var trip=store.create(TripCalculator.example());
        var assessment=store.begin(trip.id(),1);store.markRunning(assessment.id());
        leaves.searchRailServices(assessment.id());leaves.searchFlightServices(assessment.id());leaves.searchHotels(assessment.id());leaves.evaluateTripOptions(assessment.id());
        store.complete(assessment.id(),new Contracts.ModelResult(assessment.id(),"OPTIONS",new Contracts.Choice("RAIL-OUT~RAIL-RETURN~GARDEN","Quiet room."),null,"Fixture for live interpretation."),"fixture",java.util.List.of());
        store.book(trip.id(),new Contracts.BookingCommand(assessment.id(),"RAIL-OUT~RAIL-RETURN~GARDEN","live-intake-fixture"));
        var question=intake.interpret(trip.id(),new Command("Keep the quiet hotel, but get us home earlier.",null));
        assertEquals("CLARIFY",question.status());assertNull(question.proposedRequest());
        var answer=intake.interpret(trip.id(),new Command("Back in Boston by 9:30 pm Eastern on Sunday.",question.id()));
        assertEquals("READY",answer.status());assertTrue(answer.proposedRequest().returnToOriginBy().contains("21:30"));assertEquals(1,answer.differences().size());
        assertEquals(trip.request(),store.get(trip.id()).request());
        var budget=intake.interpret(trip.id(),new Command("I can spend another $100.",null));
        assertEquals("READY",budget.status());assertEquals(130000,budget.proposedRequest().budgetCents());assertEquals(1,budget.differences().size());
        var unsupported=intake.interpret(trip.id(),new Command("Change the destination to Paris and make the budget $2000.",null));
        assertEquals("UNSUPPORTED",unsupported.status());assertNull(unsupported.proposedRequest());assertEquals(1,store.get(trip.id()).revision());
        var conditional=intake.interpret(trip.id(),new Command("Flights are okay if the train is canceled.",null));
        assertEquals("CLARIFY",conditional.status());
        System.out.println("LIVE_INTAKE_SESSION="+answer.sessionId());
    }
}
