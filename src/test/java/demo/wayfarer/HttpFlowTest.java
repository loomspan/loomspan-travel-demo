package demo.wayfarer;

import ai.loomspan.api.SkillTemplate;
import ai.loomspan.api.SkillExecutionView;
import ai.loomspan.api.SkillException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.util.List;
import java.util.function.Consumer;
import static demo.wayfarer.Contracts.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="spring.datasource.url=jdbc:h2:mem:wayfarer-http;DB_CLOSE_DELAY=-1")
class HttpFlowTest {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired TravelSkills leaves;
    @MockitoBean SkillTemplate skills;
    private final HttpClient client=HttpClient.newHttpClient();
    HttpResponse<String> request(String path,String method,Object body) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path));
        if(body==null)builder.method(method,HttpRequest.BodyPublishers.noBody());
        else builder.header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        return client.send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void httpRequestAssessmentBookingAndReload() throws Exception {
        when(skills.invoke(eq("planTrip"),any(Object.class),any())).thenAnswer(invocation->{
            PlanningInput input=invocation.getArgument(1);Consumer<SkillExecutionView> observer=invocation.getArgument(2);
            leaves.searchRailServices(input.assessmentId());leaves.searchFlightServices(input.assessmentId());leaves.searchHotels(input.assessmentId());leaves.evaluateTripOptions(input.assessmentId());
            observer.accept(new SkillExecutionView("http-test-session",List.of()));
            return json.writeValueAsString(new ModelResult(input.assessmentId(),"OPTIONS",new Choice("RAIL-OUT~RAIL-RETURN~GARDEN","Quiet room."),new Choice("RAIL-OUT~RAIL-RETURN~CENTRAL","Save money."),"Two verified alternatives."));
        });
        var created=request("/api/trips","POST",TripCalculator.example());assertEquals(201,created.statusCode());var trip=json.readValue(created.body(),TripView.class);
        var started=request("/api/trips/"+trip.id()+"/assessments","POST",new AssessmentCommand(1));assertEquals(202,started.statusCode());
        var a=await(json.readValue(started.body(),AssessmentView.class).id());assertEquals("SUCCEEDED",a.status());assertEquals("http-test-session",a.sessionId());
        var command=new BookingCommand(a.id(),a.result().recommended().candidateId(),"http-test-key");
        var booked=request("/api/trips/"+trip.id()+"/bookings","POST",command);assertEquals(200,booked.statusCode(),booked.body());
        assertEquals(booked.body(),request("/api/trips/"+trip.id()+"/bookings","POST",command).body());
        var restored=json.readValue(request("/api/trips/"+trip.id(),"GET",null).body(),TripView.class);assertNotNull(restored.booking());assertEquals(98000,restored.booking().quote().totalCents());
        assertEquals(409,request("/api/trips/"+trip.id()+"/assessments","POST",new AssessmentCommand(1)).statusCode());
    }
    @Test void providerFailureIsNotInfeasibility() throws Exception {
        when(skills.invoke(eq("planTrip"),any(Object.class),any())).thenThrow(new SkillException("Provider failed"));
        var trip=json.readValue(request("/api/trips","POST",TripCalculator.example()).body(),TripView.class);
        var a=json.readValue(request("/api/trips/"+trip.id()+"/assessments","POST",new AssessmentCommand(1)).body(),AssessmentView.class);
        a=await(a.id());assertEquals("FAILED",a.status());assertNull(a.result());assertNull(a.sessionId());assertNotNull(a.error());
    }
    @Test void malformedJsonAndUnknownIdsReturnUsefulHttpStatuses() throws Exception {
        assertEquals(404,request("/api/trips/not-found","GET",null).statusCode());
        assertEquals(400,request("/api/trips","POST",java.util.Map.of("budgetCents","not-a-number")).statusCode());
    }
    AssessmentView await(String id) throws Exception {
        for(int i=0;i<100;i++) {var a=json.readValue(request("/api/assessments/"+id,"GET",null).body(),AssessmentView.class);if(!List.of("QUEUED","RUNNING").contains(a.status()))return a;Thread.sleep(100);}
        throw new AssertionError("Assessment did not complete");
    }
}
