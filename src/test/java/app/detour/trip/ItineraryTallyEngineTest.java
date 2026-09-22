package app.detour.trip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ItineraryTallyEngineTest {

    private ItineraryTallyEngine engine;

    @BeforeEach
    void setUp() {
        engine = new ItineraryTallyEngine();
    }

    @Test
    void calculateAirfareTotal_multiTraveler_calculatesSumOfLegsMultipliedByTravelers() {
        AirfareSelection airfare = new AirfareSelection(
                1L, 2L,
                "Flight 101", "Flight 102",
                20_000L, 2_000L, 1_000L, // Outbound = 23,000 cents
                18_000L, 2_000L, 1_000L  // Return = 21,000 cents
        );
        long total = engine.calculateAirfareTotal(airfare, 3);
        // (23000 + 21000) * 3 = 132,000 cents
        assertEquals(132_000L, total);
    }

    @Test
    void calculateAirfareTotal_singleTraveler() {
        AirfareSelection airfare = new AirfareSelection(
                1L, 2L,
                "Flight 101", "Flight 102",
                10_000L, 1_000L, 500L, // Outbound = 11,500
                12_000L, 1_000L, 500L  // Return = 13,500
        );
        long total = engine.calculateAirfareTotal(airfare, 1);
        assertEquals(25_000L, total);
    }

    @Test
    void calculateAirfareTotal_nullOrZeroTravelers_returnsZero() {
        AirfareSelection airfare = new AirfareSelection(
                1L, 2L, "Out", "Ret",
                10_000L, 1_000L, 500L,
                10_000L, 1_000L, 500L
        );
        assertEquals(0L, engine.calculateAirfareTotal(null, 2));
        assertEquals(0L, engine.calculateAirfareTotal(airfare, 0));
        assertEquals(0L, engine.calculateAirfareTotal(airfare, -1));
    }

    @Test
    void calculateStayTotal_multiNightMultiUnit_sumsNightsMultipliedByUnitCount() {
        StayNight night1 = new StayNight(LocalDate.of(2027, 3, 1), 15_000L, 1_500L, 500L); // 17,000
        StayNight night2 = new StayNight(LocalDate.of(2027, 3, 2), 18_000L, 1_800L, 500L); // 20,300
        StaySelection stay = new StaySelection(10L, 2, "Grand Hotel", "Standard Room", List.of(night1, night2));

        long total = engine.calculateStayTotal(stay);
        // (17000 + 20300) * 2 = 74,600 cents
        assertEquals(74_600L, total);
    }

    @Test
    void calculateStayTotal_singleNightSingleUnit() {
        StayNight night = new StayNight(LocalDate.of(2027, 3, 1), 10_000L, 1_000L, 500L);
        StaySelection stay = new StaySelection(10L, 1, "Hotel", "Room", List.of(night));
        assertEquals(11_500L, engine.calculateStayTotal(stay));
    }

    @Test
    void calculateStayTotal_nullOrEmptyNights_returnsZero() {
        assertEquals(0L, engine.calculateStayTotal(null));
        StaySelection emptyNights = new StaySelection(10L, 1, "Hotel", "Room", List.of());
        assertEquals(0L, engine.calculateStayTotal(emptyNights));
        StaySelection nullNights = new StaySelection(10L, 1, "Hotel", "Room", null);
        assertEquals(0L, engine.calculateStayTotal(nullNights));
        StaySelection zeroUnits = new StaySelection(10L, 0, "Hotel", "Room", List.of(new StayNight(LocalDate.of(2027, 3, 1), 100L, 10L, 5L)));
        assertEquals(0L, engine.calculateStayTotal(zeroUnits));
    }

    @Test
    void calculateRentalTotal_exact24Hours_returnsSingleCycle() {
        OffsetDateTime pickup = OffsetDateTime.of(2027, 3, 1, 10, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime returnAt = OffsetDateTime.of(2027, 3, 2, 10, 0, 0, 0, ZoneOffset.UTC);
        RentalSelection rental = new RentalSelection(
                5L, pickup, returnAt, "Airport", "Compact", "C1",
                4_000L, 400L, 200L // Daily = 4,600 cents
        );
        assertEquals(4_600L, engine.calculateRentalTotal(rental));
    }

    @Test
    void calculateRentalTotal_partialCycleRoundUp_returnsCeilCycles() {
        OffsetDateTime pickup = OffsetDateTime.of(2027, 3, 1, 10, 0, 0, 0, ZoneOffset.UTC);

        // 24h + 1s -> 2 cycles
        OffsetDateTime return24h1s = OffsetDateTime.of(2027, 3, 2, 10, 0, 1, 0, ZoneOffset.UTC);
        RentalSelection rental24h1s = new RentalSelection(5L, pickup, return24h1s, "Loc", "Compact", "C1", 4_000L, 400L, 200L);
        assertEquals(9_200L, engine.calculateRentalTotal(rental24h1s));

        // 25 hours -> 2 cycles
        OffsetDateTime return25h = OffsetDateTime.of(2027, 3, 2, 11, 0, 0, 0, ZoneOffset.UTC);
        RentalSelection rental25h = new RentalSelection(5L, pickup, return25h, "Loc", "Compact", "C1", 4_000L, 400L, 200L);
        assertEquals(9_200L, engine.calculateRentalTotal(rental25h));

        // 48 hours -> 2 cycles
        OffsetDateTime return48h = OffsetDateTime.of(2027, 3, 3, 10, 0, 0, 0, ZoneOffset.UTC);
        RentalSelection rental48h = new RentalSelection(5L, pickup, return48h, "Loc", "Compact", "C1", 4_000L, 400L, 200L);
        assertEquals(9_200L, engine.calculateRentalTotal(rental48h));

        // 49 hours -> 3 cycles
        OffsetDateTime return49h = OffsetDateTime.of(2027, 3, 3, 11, 0, 0, 0, ZoneOffset.UTC);
        RentalSelection rental49h = new RentalSelection(5L, pickup, return49h, "Loc", "Compact", "C1", 4_000L, 400L, 200L);
        assertEquals(13_800L, engine.calculateRentalTotal(rental49h));
    }

    @Test
    void calculateRentalTotal_nullRentalOrMissingDatesFallback() {
        assertEquals(0L, engine.calculateRentalTotal(null));

        // Missing dates -> defaults to 1 cycle
        RentalSelection rentalNoDates = new RentalSelection(5L, null, null, "Loc", "Compact", "C1", 3_000L, 300L, 200L);
        assertEquals(3_500L, engine.calculateRentalTotal(rentalNoDates));
    }

    @Test
    void calculateTally_withinBudget_calculatesPositiveRemainingAndZeroOverage() {
        AirfareSelection airfare = new AirfareSelection(1L, 2L, "A", "B", 20_000L, 0L, 0L, 20_000L, 0L, 0L);
        DraftSelections selections = new DraftSelections(airfare, null, null);

        ItineraryTallyResponse tally = engine.calculateTally(selections, 2, 100_000L);
        assertEquals(80_000L, tally.airfareTotalCents());
        assertEquals(0L, tally.stayTotalCents());
        assertEquals(0L, tally.rentalTotalCents());
        assertEquals(80_000L, tally.grandTotalCents());
        assertEquals(20_000L, tally.remainingBudgetCents());
        assertEquals(0L, tally.budgetOverageCents());
        assertFalse(tally.isOverBudget());
    }

    @Test
    void calculateTally_exactBudget_returnsZeroRemainingAndZeroOverage() {
        AirfareSelection airfare = new AirfareSelection(1L, 2L, "A", "B", 25_000L, 0L, 0L, 25_000L, 0L, 0L);
        DraftSelections selections = new DraftSelections(airfare, null, null);

        ItineraryTallyResponse tally = engine.calculateTally(selections, 2, 100_000L);
        assertEquals(100_000L, tally.grandTotalCents());
        assertEquals(0L, tally.remainingBudgetCents());
        assertEquals(0L, tally.budgetOverageCents());
        assertFalse(tally.isOverBudget());
    }

    @Test
    void calculateTally_overBudget_returnsZeroRemainingAndPositiveOverageWithFlag() {
        AirfareSelection airfare = new AirfareSelection(1L, 2L, "A", "B", 30_000L, 0L, 0L, 30_000L, 0L, 0L);
        DraftSelections selections = new DraftSelections(airfare, null, null);

        ItineraryTallyResponse tally = engine.calculateTally(selections, 2, 100_000L);
        assertEquals(120_000L, tally.grandTotalCents());
        assertEquals(0L, tally.remainingBudgetCents());
        assertEquals(20_000L, tally.budgetOverageCents());
        assertTrue(tally.isOverBudget());
    }

    @Test
    void calculateTally_zeroBudget_returnsZeroRemainingAndOverBudgetWhenNonZero() {
        // Zero budget with components
        AirfareSelection airfare = new AirfareSelection(1L, 2L, "A", "B", 5_000L, 0L, 0L, 0L, 0L, 0L);
        DraftSelections selections = new DraftSelections(airfare, null, null);

        ItineraryTallyResponse tally = engine.calculateTally(selections, 1, 0L);
        assertEquals(5_000L, tally.grandTotalCents());
        assertEquals(0L, tally.remainingBudgetCents());
        assertEquals(5_000L, tally.budgetOverageCents());
        assertTrue(tally.isOverBudget());

        // Zero budget with zero components
        ItineraryTallyResponse emptyTally = engine.calculateTally(null, 1, 0L);
        assertEquals(0L, emptyTally.grandTotalCents());
        assertEquals(0L, emptyTally.remainingBudgetCents());
        assertEquals(0L, emptyTally.budgetOverageCents());
        assertFalse(emptyTally.isOverBudget());
    }

    @Test
    void calculateTally_nullBudget_returnsNullRemainingAndNullOverage() {
        AirfareSelection airfare = new AirfareSelection(1L, 2L, "A", "B", 25_000L, 0L, 0L, 25_000L, 0L, 0L);
        DraftSelections selections = new DraftSelections(airfare, null, null);

        ItineraryTallyResponse tally = engine.calculateTally(selections, 1, null);
        assertEquals(50_000L, tally.grandTotalCents());
        assertNull(tally.remainingBudgetCents());
        assertNull(tally.budgetOverageCents());
        assertFalse(tally.isOverBudget());
    }

    @Test
    void calculateTally_allEightComponentCombinations() {
        AirfareSelection air = new AirfareSelection(1L, 2L, "A", "B", 10_000L, 0L, 0L, 10_000L, 0L, 0L); // 20,000 * 2 = 40,000
        StaySelection stay = new StaySelection(1L, 1, "P", "U", List.of(new StayNight(LocalDate.of(2027, 3, 1), 30_000L, 0L, 0L))); // 30,000
        OffsetDateTime t1 = OffsetDateTime.of(2027, 3, 1, 10, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime t2 = OffsetDateTime.of(2027, 3, 2, 10, 0, 0, 0, ZoneOffset.UTC);
        RentalSelection rental = new RentalSelection(1L, t1, t2, "L", "C", "U", 15_000L, 0L, 0L); // 15,000 * 1 = 15,000

        long airCost = 40_000L;
        long stayCost = 30_000L;
        long rentCost = 15_000L;
        Long budget = 100_000L;

        // 1. Empty
        ItineraryTallyResponse t1Res = engine.calculateTally(new DraftSelections(null, null, null), 2, budget);
        assertEquals(0L, t1Res.grandTotalCents());

        // 2. Airfare only
        ItineraryTallyResponse t2Res = engine.calculateTally(new DraftSelections(air, null, null), 2, budget);
        assertEquals(airCost, t2Res.grandTotalCents());
        assertEquals(airCost, t2Res.airfareTotalCents());
        assertEquals(0L, t2Res.stayTotalCents());
        assertEquals(0L, t2Res.rentalTotalCents());

        // 3. Stay only
        ItineraryTallyResponse t3Res = engine.calculateTally(new DraftSelections(null, stay, null), 2, budget);
        assertEquals(stayCost, t3Res.grandTotalCents());
        assertEquals(0L, t3Res.airfareTotalCents());
        assertEquals(stayCost, t3Res.stayTotalCents());

        // 4. Rental only
        ItineraryTallyResponse t4Res = engine.calculateTally(new DraftSelections(null, null, rental), 2, budget);
        assertEquals(rentCost, t4Res.grandTotalCents());
        assertEquals(rentCost, t4Res.rentalTotalCents());

        // 5. Airfare + Stay
        ItineraryTallyResponse t5Res = engine.calculateTally(new DraftSelections(air, stay, null), 2, budget);
        assertEquals(airCost + stayCost, t5Res.grandTotalCents());

        // 6. Stay + Rental
        ItineraryTallyResponse t6Res = engine.calculateTally(new DraftSelections(null, stay, rental), 2, budget);
        assertEquals(stayCost + rentCost, t6Res.grandTotalCents());

        // 7. Airfare + Rental
        ItineraryTallyResponse t7Res = engine.calculateTally(new DraftSelections(air, null, rental), 2, budget);
        assertEquals(airCost + rentCost, t7Res.grandTotalCents());

        // 8. All three
        ItineraryTallyResponse t8Res = engine.calculateTally(new DraftSelections(air, stay, rental), 2, budget);
        assertEquals(airCost + stayCost + rentCost, t8Res.grandTotalCents());
        assertEquals(budget - (airCost + stayCost + rentCost), t8Res.remainingBudgetCents());
    }

    @Test
    void calculateAvailableSearchBudgets_deductsOtherComponentsAndExcludesSearchedComponent() {
        AirfareSelection air = new AirfareSelection(1L, 2L, "A", "B", 15_000L, 0L, 0L, 15_000L, 0L, 0L); // 30,000 * 1 = 30,000
        StaySelection stay = new StaySelection(1L, 1, "P", "U", List.of(new StayNight(LocalDate.of(2027, 3, 1), 40_000L, 0L, 0L))); // 40,000
        OffsetDateTime t1 = OffsetDateTime.of(2027, 3, 1, 10, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime t2 = OffsetDateTime.of(2027, 3, 2, 10, 0, 0, 0, ZoneOffset.UTC);
        RentalSelection rental = new RentalSelection(1L, t1, t2, "L", "C", "U", 15_000L, 0L, 0L); // 15,000

        DraftSelections allThree = new DraftSelections(air, stay, rental);
        Long budget = 100_000L;

        // Stay search with flight (30000) and car (15000) selected against budget (100000) -> 100000 - 30000 - 15000 = 55000
        // Existing stay (40000) is excluded/not deducted
        Long availableStayBudget = engine.calculateAvailableStaySearchBudget(budget, allThree, 1);
        assertEquals(55_000L, availableStayBudget);

        // Rental search with flight (30000) and stay (40000) selected against budget (100000) -> 100000 - 30000 - 40000 = 30000
        // Existing rental (15000) is excluded/not deducted
        Long availableRentalBudget = engine.calculateAvailableRentalSearchBudget(budget, allThree, 1);
        assertEquals(30_000L, availableRentalBudget);

        // When budget is null, available budget is null
        assertNull(engine.calculateAvailableStaySearchBudget(null, allThree, 1));
        assertNull(engine.calculateAvailableRentalSearchBudget(null, allThree, 1));

        // When selections are null
        assertEquals(100_000L, engine.calculateAvailableStaySearchBudget(budget, null, 1));
        assertEquals(100_000L, engine.calculateAvailableRentalSearchBudget(budget, null, 1));
    }
}
