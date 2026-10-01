package app.detour.trip;

public record ItineraryTallyResponse(
        long airfareTotalCents,
        long stayTotalCents,
        long rentalTotalCents,
        long grandTotalCents,
        Long remainingBudgetCents,
        Long budgetOverageCents,
        boolean isOverBudget, boolean partial
) {
    public ItineraryTallyResponse(long airfareTotalCents, long stayTotalCents, long rentalTotalCents, long grandTotalCents, Long remainingBudgetCents, Long budgetOverageCents, boolean isOverBudget) {
        this(airfareTotalCents, stayTotalCents, rentalTotalCents, grandTotalCents, remainingBudgetCents, budgetOverageCents, isOverBudget, false);
    }
}
