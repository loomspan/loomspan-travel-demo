package app.detour.trip;

import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class ItineraryTallyEngine {

    public long calculateAirfareTotal(AirfareSelection airfare, int travelerCount) {
        if (airfare == null || travelerCount <= 0) {
            return 0L;
        }
        long outbound = airfare.outboundBaseFareCents() + airfare.outboundTaxCents() + airfare.outboundFeeCents();
        long inbound = airfare.returnBaseFareCents() + airfare.returnTaxCents() + airfare.returnFeeCents();
        return (outbound + inbound) * travelerCount;
    }

    public long calculateStayTotal(StaySelection stay) {
        if (stay == null || stay.nights() == null || stay.nights().isEmpty() || stay.unitCount() <= 0) {
            return 0L;
        }
        long perRoom = stay.nights().stream()
                .mapToLong(n -> n.basePriceCents() + n.taxCents() + n.feeCents())
                .sum();
        return perRoom * stay.unitCount();
    }

    public long calculateRentalTotal(RentalSelection rental) {
        if (rental == null) {
            return 0L;
        }
        long dailyRate = rental.dailyBasePriceCents() + rental.dailyTaxCents() + rental.dailyFeeCents();
        long billingCycles = 1;
        if (rental.pickupAt() != null && rental.returnAt() != null) {
            long seconds = Duration.between(rental.pickupAt(), rental.returnAt()).getSeconds();
            billingCycles = Math.max(1, (seconds + 86399) / 86400);
        }
        return billingCycles * dailyRate;
    }

    public long calculateGrandTotal(long airfareTotal, long stayTotal, long rentalTotal) {
        return airfareTotal + stayTotal + rentalTotal;
    }

    public ItineraryTallyResponse calculateTally(DraftSelections selections, int travelerCount, Long budgetCents) {
        long airfareTotal = selections != null ? calculateAirfareTotal(selections.airfare(), travelerCount) : 0L;
        long stayTotal = selections != null ? calculateStayTotal(selections.stay()) : 0L;
        long rentalTotal = selections != null ? calculateRentalTotal(selections.rental()) : 0L;
        long grandTotal = calculateGrandTotal(airfareTotal, stayTotal, rentalTotal);

        Long remainingBudgetCents;
        Long budgetOverageCents;
        boolean isOverBudget;

        if (budgetCents != null) {
            long budget = budgetCents;
            if (grandTotal <= budget) {
                remainingBudgetCents = budget - grandTotal;
                budgetOverageCents = 0L;
                isOverBudget = false;
            } else {
                remainingBudgetCents = 0L;
                budgetOverageCents = grandTotal - budget;
                isOverBudget = true;
            }
        } else {
            remainingBudgetCents = null;
            budgetOverageCents = null;
            isOverBudget = false;
        }

        return new ItineraryTallyResponse(
                airfareTotal,
                stayTotal,
                rentalTotal,
                grandTotal,
                remainingBudgetCents,
                budgetOverageCents,
                isOverBudget
        );
    }

    public Long calculateAvailableStaySearchBudget(Long budgetCents, DraftSelections selections, int travelerCount) {
        if (budgetCents == null) {
            return null;
        }
        long airfareTotal = selections != null ? calculateAirfareTotal(selections.airfare(), travelerCount) : 0L;
        long rentalTotal = selections != null ? calculateRentalTotal(selections.rental()) : 0L;
        return budgetCents - airfareTotal - rentalTotal;
    }

    public Long calculateAvailableRentalSearchBudget(Long budgetCents, DraftSelections selections, int travelerCount) {
        if (budgetCents == null) {
            return null;
        }
        long airfareTotal = selections != null ? calculateAirfareTotal(selections.airfare(), travelerCount) : 0L;
        long stayTotal = selections != null ? calculateStayTotal(selections.stay()) : 0L;
        return budgetCents - airfareTotal - stayTotal;
    }
}
