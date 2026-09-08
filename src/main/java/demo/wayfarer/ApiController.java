package demo.wayfarer;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.util.List;
import java.util.Map;
import static demo.wayfarer.Contracts.*;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final TripStore store; private final AssessmentService assessments; private final IntakeService intake;
    public ApiController(TripStore store,AssessmentService assessments,IntakeService intake) {this.store=store;this.assessments=assessments;this.intake=intake;}
    @PostMapping(value="/trips/{id}/intakes",consumes="application/json") public IntakeContracts.View interpret(@PathVariable String id,@RequestBody IntakeContracts.Command command) {return intake.interpret(id,command);}
    @GetMapping("/trips/{id}/intakes/latest") public IntakeContracts.Latest latestIntake(@PathVariable String id) {return new IntakeContracts.Latest(intake.latest(id));}
    @PostMapping("/trips/{id}/intakes/{draftId}/confirmation") public TripView confirmIntake(@PathVariable String id,@PathVariable String draftId) {return intake.confirm(id,draftId);}
    @GetMapping("/example") public TripRequest example() {return TripCalculator.example();}
    @GetMapping("/trips") public List<TripSummary> list() {return store.list();}
    @GetMapping("/trips/{id}") public TripView get(@PathVariable String id) {return store.get(id);}
    @PostMapping(value="/trips",consumes="application/json") public ResponseEntity<TripView> create(@RequestBody TripRequest request) {return ResponseEntity.status(201).body(store.create(request));}
    @PutMapping(value="/trips/{id}",consumes="application/json") public TripView revise(@PathVariable String id,@RequestBody RevisionCommand command) {return store.revise(id,command);}
    @PostMapping(value="/trips/{id}/assessments",consumes="application/json") public ResponseEntity<AssessmentView> assess(@PathVariable String id,@RequestBody AssessmentCommand command) {return ResponseEntity.accepted().body(assessments.start(id,command.revision()));}
    @PostMapping(value="/trips/{id}/return-cancellation",consumes="application/json") public TripView cancelReturn(@PathVariable String id,@RequestBody CancellationCommand command) {return store.cancelReturn(id,command);}
    @GetMapping("/assessments/{id}") public AssessmentView assessment(@PathVariable String id) {return store.assessment(id);}
    @PostMapping(value="/trips/{id}/bookings",consumes="application/json") public BookingView book(@PathVariable String id,@RequestBody BookingCommand command) {return store.book(id,command);}
    @PostMapping(value="/trips/{id}/exchanges",consumes="application/json") public BookingView exchange(@PathVariable String id,@RequestBody BookingCommand command) {return store.exchange(id,command);}
}

@RestControllerAdvice
class ApiErrors {
    @ExceptionHandler(ApiProblem.class) ResponseEntity<Map<String,String>> domain(ApiProblem e) {return ResponseEntity.status(e.status).body(Map.of("code",e.code,"message",e.getMessage()));}
    @ExceptionHandler(HttpMessageNotReadableException.class) ResponseEntity<Map<String,String>> invalid(HttpMessageNotReadableException e) {return ResponseEntity.badRequest().body(Map.of("code","INVALID_JSON","message","Request fields have missing or invalid values."));}
}
