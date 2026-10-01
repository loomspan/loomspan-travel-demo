package app.detour.trip;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
public record PlanResponse(UUID id, String name, LocalDate startDate, LocalDate endDate, int travelerCount,
        List<Integer> travelerAges, long version, DraftSelectionResponse selections, ItineraryTallyResponse tally,
        boolean primary, boolean booked, List<String> lockedComponents, app.detour.booking.BookingResponse purchase) { }
