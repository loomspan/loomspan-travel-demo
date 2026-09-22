package app.detour.stay;

import app.detour.stay.StaySearchResponses.StayNightPricingResponse;
import app.detour.stay.StaySearchResponses.StayOptionResponse;
import app.detour.stay.StaySearchResponses.StayPricingResponse;
import app.detour.stay.StaySearchResponses.StaySearchResponse;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class StaySearchService {
    private final StaySearchRepository staySearchRepository;

    public StaySearchService(StaySearchRepository staySearchRepository) {
        this.staySearchRepository = staySearchRepository;
    }

    public StaySearchResponse search(
            long destinationId,
            String destinationKey,
            LocalDate startDate,
            LocalDate endDate,
            int travelerCount,
            UUID tripId,
            UUID draftId,
            AccommodationType type,
            StaySort sort,
            Long availableTripBudgetCents) {

        StaySort effectiveSort = (sort == null) ? StaySort.DEFAULT : sort;
        long requiredNights = ChronoUnit.DAYS.between(startDate, endDate);
        if (requiredNights <= 0) {
            return new StaySearchResponse(
                    tripId,
                    draftId,
                    destinationKey,
                    type,
                    startDate,
                    endDate,
                    travelerCount,
                    availableTripBudgetCents,
                    effectiveSort,
                    List.of()
            );
        }

        List<StayCandidate> candidates = staySearchRepository.findCandidates(destinationId, type, startDate, endDate);
        List<StayOptionResponse> options = new ArrayList<>();

        for (StayCandidate candidate : candidates) {
            // 1. Automatic room-count calculation & whole-property capacity filter
            int requiredRooms;
            if (type == AccommodationType.VACATION_RENTAL || "WHOLE_PROPERTY".equals(candidate.unitKind())) {
                if (candidate.guestCapacity() < travelerCount) {
                    continue; // Exclude vacation rental when guest capacity is less than traveler count
                }
                requiredRooms = 1;
            } else {
                requiredRooms = (int) Math.ceil((double) travelerCount / candidate.guestCapacity());
            }

            // 2. Multi-night inventory check
            if (candidate.nights().size() != requiredNights) {
                continue; // Insufficient nightly records in the date range
            }
            boolean hasSufficientInventory = candidate.nights().stream()
                    .allMatch(night -> night.availableInventory() >= requiredRooms);
            if (!hasSufficientInventory) {
                continue; // Insufficient inventory on at least one night
            }

            // 3. Complete stay pricing calculation
            List<StayNightPricingResponse> nightResponses = new ArrayList<>();
            long perRoomBase = 0;
            long perRoomTax = 0;
            long perRoomFee = 0;

            for (StayCandidate.CandidateNight night : candidate.nights()) {
                long nightTotal = night.basePriceCents() + night.taxCents() + night.feeCents();
                perRoomBase += night.basePriceCents();
                perRoomTax += night.taxCents();
                perRoomFee += night.feeCents();

                nightResponses.add(new StayNightPricingResponse(
                        night.date(),
                        night.basePriceCents(),
                        night.taxCents(),
                        night.feeCents(),
                        nightTotal,
                        night.availableInventory()
                ));
            }

            long perRoomTotal = perRoomBase + perRoomTax + perRoomFee;
            long totalBase = perRoomBase * requiredRooms;
            long totalTax = perRoomTax * requiredRooms;
            long totalFee = perRoomFee * requiredRooms;
            long totalPrice = perRoomTotal * requiredRooms;

            StayPricingResponse pricing = new StayPricingResponse(
                    requiredRooms,
                    (int) requiredNights,
                    perRoomBase,
                    perRoomTax,
                    perRoomFee,
                    perRoomTotal,
                    totalBase,
                    totalTax,
                    totalFee,
                    totalPrice,
                    nightResponses
            );

            // 4. Budget fit evaluation
            Boolean fitsBudget = (availableTripBudgetCents != null)
                    ? (totalPrice <= availableTripBudgetCents)
                    : null;

            options.add(new StayOptionResponse(
                    candidate.unitId(),
                    candidate.propertyId(),
                    candidate.propertyCatalogKey(),
                    candidate.unitCatalogKey(),
                    candidate.propertyName(),
                    candidate.unitName(),
                    candidate.propertyCategory(),
                    candidate.unitKind(),
                    candidate.locationDescription(),
                    candidate.guestRating(),
                    candidate.distanceToCityCenterMeters(),
                    candidate.latitude(),
                    candidate.longitude(),
                    candidate.guestCapacity(),
                    candidate.inventoryCapacity(),
                    pricing,
                    fitsBudget
            ));
        }

        // 5. Deterministic sorting
        Comparator<StayOptionResponse> comparator = switch (effectiveSort) {
            case DEFAULT -> {
                if (availableTripBudgetCents != null) {
                    yield Comparator
                            .comparing((StayOptionResponse o) -> !Boolean.TRUE.equals(o.fitsBudget()))
                            .thenComparing(StayOptionResponse::guestRating, Comparator.reverseOrder())
                            .thenComparingLong(o -> o.pricing().totalPriceCents())
                            .thenComparingInt(StayOptionResponse::distanceToCityCenterMeters)
                            .thenComparing(StayOptionResponse::propertyCatalogKey);
                } else {
                    yield Comparator
                            .comparing(StayOptionResponse::guestRating, Comparator.reverseOrder())
                            .thenComparingLong(o -> o.pricing().totalPriceCents())
                            .thenComparingInt(StayOptionResponse::distanceToCityCenterMeters)
                            .thenComparing(StayOptionResponse::propertyCatalogKey);
                }
            }
            case LOWEST_PRICE -> Comparator
                    .comparingLong((StayOptionResponse o) -> o.pricing().totalPriceCents())
                    .thenComparing(StayOptionResponse::propertyCatalogKey);
            case HIGHEST_RATING -> Comparator
                    .comparing(StayOptionResponse::guestRating, Comparator.reverseOrder())
                    .thenComparing(StayOptionResponse::propertyCatalogKey);
            case NEAREST_CITY_CENTER -> Comparator
                    .comparingInt(StayOptionResponse::distanceToCityCenterMeters)
                    .thenComparing(StayOptionResponse::propertyCatalogKey);
        };

        options.sort(comparator);

        return new StaySearchResponse(
                tripId,
                draftId,
                destinationKey,
                type,
                startDate,
                endDate,
                travelerCount,
                availableTripBudgetCents,
                effectiveSort,
                options
        );
    }
}
