package demo.wayfarer;

import ai.loomspan.api.SkillMethod;
import ai.loomspan.api.SkillParam;
import org.springframework.stereotype.Component;
import java.util.List;
import static demo.wayfarer.Contracts.*;

@Component
public class TravelSkills {
    private final TripStore store; private final TripCalculator calculator;
    public TravelSkills(TripStore store,TripCalculator calculator) { this.store=store; this.calculator=calculator; }
    @SkillMethod(description="Read all eligible rail services from the immutable assessment catalog. Returns outbound and return alternatives; do not prune by fare.")
    public SearchResult searchRailServices(@SkillParam(description="Exact assessmentId from the parent request") String assessmentId) { return search(assessmentId,"rail"); }
    @SkillMethod(description="Read all eligible flight services from the immutable assessment catalog. Flight mode must be allowed in the saved request.")
    public SearchResult searchFlightServices(@SkillParam(description="Exact assessmentId from the parent request") String assessmentId) { return search(assessmentId,"flight"); }
    private SearchResult search(String id,String mode) {
        var s=store.snapshot(id);
        if(!s.request().allowedModes().contains(mode)) throw ApiProblem.invalid("Mode not allowed by saved request.");
        var result=new SearchResult(calculator.eligibleServices(s,"outbound").stream().filter(x->x.mode().equals(mode)).toList(),calculator.eligibleServices(s,"return").stream().filter(x->x.mode().equals(mode)).toList(),true);
        store.recordReceipt(id,mode.toUpperCase(),result);
        return result;
    }
    @SkillMethod(description="Read all hotels with sufficient occupancy and availability for both nights. Preserve the quiet-room and cheaper options; no price pruning.")
    public HotelSearch searchHotels(@SkillParam(description="Exact assessmentId from the parent request") String assessmentId) {
        var result=new HotelSearch(calculator.eligibleHotels(store.snapshot(assessmentId)),true);
        store.recordReceipt(assessmentId,"HOTELS",result);
        return result;
    }
    @SkillMethod(description="Load the immutable catalog results already produced by transport and stay searches for this assessment. Require every allowed mode and hotels to have been searched, validate complete coverage, then calculate all whole-trip totals, timing, violations and blockers. No model-transcribed inventory identifiers are needed.")
    public EvaluatedRequest evaluateTripOptions(
        @SkillParam(description="Exact assessmentId shared by the completed transport and stay specialists") String assessmentId) {
        var s=store.snapshot(assessmentId);
        var outboundServiceIds=new java.util.ArrayList<String>();
        var returnServiceIds=new java.util.ArrayList<String>();
        for(String mode:s.request().allowedModes()) {
            var result=store.receipt(assessmentId,mode.toUpperCase(),SearchResult.class);
            outboundServiceIds.addAll(result.outbound().stream().map(ServiceOption::id).toList());
            returnServiceIds.addAll(result.returnOptions().stream().map(ServiceOption::id).toList());
        }
        var hotelIds=store.receipt(assessmentId,"HOTELS",HotelSearch.class).hotels().stream().map(HotelOption::id).toList();
        calculator.requireCoverage(outboundServiceIds,calculator.eligibleServices(s,"outbound").stream().map(ServiceOption::id).toList());
        calculator.requireCoverage(returnServiceIds,calculator.eligibleServices(s,"return").stream().map(ServiceOption::id).toList());
        calculator.requireCoverage(hotelIds,calculator.eligibleHotels(s).stream().map(HotelOption::id).toList());
        var result=calculator.evaluate(s);
        store.recordReceipt(assessmentId,"EVALUATION",result);
        return new EvaluatedRequest(calculator.selectionOptions(s.request(),result),s.request(),result);
    }
}
