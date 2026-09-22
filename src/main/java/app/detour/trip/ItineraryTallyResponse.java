package app.detour.trip;

public record ItineraryTallyResponse(
        long airfareTotalCents,
        long stayTotalCents,
        long rentalTotalCents,
        long grandTotalCents,
        Long remainingBudgetCents,
        Long budgetOverageCents,
        boolean isOverBudget
) {
}
