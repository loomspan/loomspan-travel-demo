package demo.wayfarer;

import java.util.List;
import static demo.wayfarer.Contracts.*;

public final class IntakeContracts {
    private IntakeContracts() {}
    public record Command(String message,String parentId) {}
    public record Latest(View draft) {}
    public record Turn(String role,String text) {}
    public record Baseline(int revision,TripRequest request,String bookingId,int catalogVersion,String hotelName,boolean quietRoom,Long bookedTotalCents,boolean disrupted,String canceledReturnMode) {}
    public record Input(Baseline baseline,List<Turn> conversation) {}
    public record Patch(Long budgetCents,Long budgetDeltaCents,String hotelReadyTime,String leaveHotelTime,String returnHomeTime,List<String> allowedModes,String firstPriority) {}
    public record Result(String status,String message,Patch patch) {}
    public record Difference(String field,String before,String after) {}
    public record View(String id,String tripId,Baseline baseline,List<Turn> conversation,String status,String message,TripRequest proposedRequest,List<Difference> differences,String sessionId,Integer confirmedRevision) {
        public View confirmed(Integer revision) {return new View(id,tripId,baseline,conversation,status,message,proposedRequest,differences,sessionId,revision);}
    }
}
