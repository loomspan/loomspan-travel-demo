package app.detour.rental;

import app.detour.api.ApiException;
import app.detour.rental.RentalSearchResponses.RentalOptionResponse;
import app.detour.rental.RentalSearchResponses.RentalPricingResponse;
import app.detour.rental.RentalSearchResponses.RentalSearchResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RentalSearchService {
    public static final String DRIVER_AGE_EXPLANATION =
            "Rental cars require at least one traveler aged 25 or older. Please add traveler ages in Trip Details to select a car.";

    private final RentalSearchRepository rentalSearchRepository;

    public RentalSearchService(RentalSearchRepository rentalSearchRepository) {
        this.rentalSearchRepository = rentalSearchRepository;
    }

    public RentalSearchResponse search(
            long destinationId,
            String destinationKey,
            LocalDate startDate,
            LocalDate endDate,
            List<Integer> travelerAges,
            UUID tripId,
            UUID draftId,
            OffsetDateTime pickupAt,
            OffsetDateTime returnAt,
            RentalSort sort,
            Long availableTripBudgetCents
    ) {
        validateInterval(destinationId, startDate, endDate, pickupAt, returnAt);

        RentalSort effectiveSort = (sort == null) ? RentalSort.DEFAULT : sort;
        int billingCycles = calculateBillingCycles(pickupAt, returnAt);

        boolean driverEligible = isDriverEligible(travelerAges);
        boolean selectionDisabled = !driverEligible;
        String explanation = driverEligible ? null : DRIVER_AGE_EXPLANATION;

        List<RentalCandidate> candidates = rentalSearchRepository.findAvailableUnits(destinationId, pickupAt, returnAt);
        List<RentalOptionResponse> options = new ArrayList<>();

        for (RentalCandidate candidate : candidates) {
            RentalPricingResponse pricing = computePricing(candidate, billingCycles);
            Boolean fitsBudget = (availableTripBudgetCents != null)
                    ? (pricing.totalPriceCents() <= availableTripBudgetCents)
                    : null;

            options.add(new RentalOptionResponse(
                    candidate.unitId(),
                    candidate.unitCatalogKey(),
                    candidate.unitIdentifier(),
                    candidate.vehicleClassId(),
                    candidate.vehicleClassCatalogKey(),
                    candidate.vehicleClassName(),
                    candidate.vehicleCategory(),
                    candidate.locationId(),
                    candidate.locationCatalogKey(),
                    candidate.locationName(),
                    candidate.airportIataCode(),
                    pricing,
                    fitsBudget
            ));
        }

        Comparator<RentalOptionResponse> comparator = switch (effectiveSort) {
            case DEFAULT -> Comparator
                    .comparingInt((RentalOptionResponse o) -> categoryRank(o.vehicleCategory()))
                    .thenComparingLong(o -> o.pricing().totalPriceCents())
                    .thenComparing(RentalOptionResponse::unitCatalogKey);
            case LOWEST_PRICE -> Comparator
                    .comparingLong((RentalOptionResponse o) -> o.pricing().totalPriceCents())
                    .thenComparing(RentalOptionResponse::unitCatalogKey);
        };

        options.sort(comparator);

        return new RentalSearchResponse(
                tripId,
                draftId,
                destinationKey,
                pickupAt,
                returnAt,
                billingCycles,
                driverEligible,
                selectionDisabled,
                explanation,
                explanation,
                availableTripBudgetCents,
                effectiveSort,
                options
        );
    }

    public void validateInterval(long destinationId, LocalDate startDate, LocalDate endDate,
            OffsetDateTime pickupAt, OffsetDateTime returnAt) {
        if (pickupAt == null) {
            throw new ApiException(400, "VALIDATION_FAILED", "One or more fields are invalid.",
                    Map.of("pickupAt", "Pickup time is required."));
        }
        if (returnAt == null) {
            throw new ApiException(400, "VALIDATION_FAILED", "One or more fields are invalid.",
                    Map.of("returnAt", "Return time is required."));
        }
        if (!pickupAt.isBefore(returnAt)) {
            throw new ApiException(400, "VALIDATION_FAILED", "One or more fields are invalid.",
                    Map.of("returnAt", "Return time must be strictly after pickup time."));
        }

        String timeZoneId = rentalSearchRepository.findDestinationAirportTimeZone(destinationId).orElse("UTC");
        ZoneId zone = ZoneId.of(timeZoneId);

        LocalDate localPickupDate = pickupAt.atZoneSameInstant(zone).toLocalDate();
        LocalDate localReturnDate = returnAt.atZoneSameInstant(zone).toLocalDate();

        if (localPickupDate.isBefore(startDate) || localPickupDate.isAfter(endDate)) {
            throw new ApiException(400, "VALIDATION_FAILED", "One or more fields are invalid.",
                    Map.of("pickupAt", "Pickup date must fall within the trip dates at the destination airport."));
        }
        if (localReturnDate.isBefore(startDate) || localReturnDate.isAfter(endDate)) {
            throw new ApiException(400, "VALIDATION_FAILED", "One or more fields are invalid.",
                    Map.of("returnAt", "Return date must fall within the trip dates at the destination airport."));
        }
    }

    public boolean isDriverEligible(List<Integer> travelerAges) {
        return travelerAges != null && travelerAges.stream().anyMatch(age -> age != null && age >= 25);
    }

    public int calculateBillingCycles(OffsetDateTime pickupAt, OffsetDateTime returnAt) {
        long totalSeconds = Duration.between(pickupAt, returnAt).getSeconds();
        return (int) Math.max(1, (totalSeconds + 86399) / 86400);
    }

    public RentalPricingResponse computePricing(RentalCandidate candidate, int billingCycles) {
        long dailyBase = candidate.dailyBasePriceCents();
        long dailyTax = candidate.dailyTaxCents();
        long dailyFee = candidate.dailyFeeCents();
        long dailyTotal = dailyBase + dailyTax + dailyFee;

        long totalBase = dailyBase * billingCycles;
        long totalTax = dailyTax * billingCycles;
        long totalFee = dailyFee * billingCycles;
        long totalPrice = dailyTotal * billingCycles;

        return new RentalPricingResponse(
                billingCycles,
                dailyBase,
                dailyTax,
                dailyFee,
                dailyTotal,
                totalBase,
                totalTax,
                totalFee,
                totalPrice
        );
    }

    private static int categoryRank(String category) {
        if ("ECONOMY".equalsIgnoreCase(category)) return 1;
        if ("STANDARD".equalsIgnoreCase(category)) return 2;
        if ("SUV".equalsIgnoreCase(category)) return 3;
        return 4;
    }
}
