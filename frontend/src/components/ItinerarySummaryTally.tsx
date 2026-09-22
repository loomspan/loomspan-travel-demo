import type {TripResponse, DraftSelectionResponse} from '../api/tripsApi';

type ItinerarySummaryTallyProps = {
  trip: TripResponse;
  selections: DraftSelectionResponse;
};

export function formatCents(cents: number): string {
  return `$${(cents / 100).toLocaleString('en-US', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`;
}

export function computeAirfareTotalCents(
  selections: DraftSelectionResponse,
  travelerCount: number
): number {
  if (!selections.airfare) return 0;
  const af = selections.airfare;
  const outboundPerTraveler = af.outboundBaseFareCents + af.outboundTaxCents + af.outboundFeeCents;
  const returnPerTraveler = af.returnBaseFareCents + af.returnTaxCents + af.returnFeeCents;
  return (outboundPerTraveler + returnPerTraveler) * travelerCount;
}

export function computeStayTotalCents(selections: DraftSelectionResponse): number {
  if (!selections.stay) return 0;
  const stay = selections.stay;
  const perRoomNightsTotal = stay.nights.reduce(
    (acc, night) => acc + night.basePriceCents + night.taxCents + night.feeCents,
    0
  );
  return perRoomNightsTotal * stay.unitCount;
}

export function computeRentalTotalCents(selections: DraftSelectionResponse): number {
  if (!selections.rental) return 0;
  const car = selections.rental;
  const start = new Date(car.pickupAt).getTime();
  const end = new Date(car.returnAt).getTime();
  const diffHours = (end - start) / (1000 * 60 * 60);
  const cycles = Math.max(1, Math.ceil(diffHours / 24));
  const dailyTotal = car.dailyBasePriceCents + car.dailyTaxCents + car.dailyFeeCents;
  return cycles * dailyTotal;
}

export function ItinerarySummaryTally({trip, selections}: ItinerarySummaryTallyProps) {
  const airfareTotal = computeAirfareTotalCents(selections, trip.travelerCount);
  const stayTotal = computeStayTotalCents(selections);
  const rentalTotal = computeRentalTotalCents(selections);
  const grandTotal = airfareTotal + stayTotal + rentalTotal;

  const hasBudget = trip.budgetCents !== null && trip.budgetCents !== undefined;
  const budgetCents = hasBudget ? trip.budgetCents! : 0;
  const isOverBudget = hasBudget && grandTotal > budgetCents;
  const remainingCents = hasBudget ? budgetCents - grandTotal : 0;
  const overageCents = hasBudget && isOverBudget ? grandTotal - budgetCents : 0;

  return (
    <section className="card itinerary-tally" aria-labelledby="tally-heading">
      <div className="tally-header">
        <h3 id="tally-heading">Trip Itinerary Summary</h3>
        {hasBudget && (
          <span
            className={`badge ${isOverBudget ? 'badge-warning' : 'badge-success'}`}
            role={isOverBudget ? 'alert' : undefined}
          >
            {isOverBudget ? 'Over Budget' : 'Within Budget'}
          </span>
        )}
      </div>

      <div className="tally-items">
        <div className="tally-item">
          <span className="tally-item-label">Airfare:</span>
          <span className="tally-item-value" data-testid="tally-airfare-price">
            {selections.airfare ? formatCents(airfareTotal) : '—'}
          </span>
        </div>
        <div className="tally-item">
          <span className="tally-item-label">Stay:</span>
          <span className="tally-item-value" data-testid="tally-stay-price">
            {selections.stay ? formatCents(stayTotal) : '—'}
          </span>
        </div>
        <div className="tally-item">
          <span className="tally-item-label">Rental Car:</span>
          <span className="tally-item-value" data-testid="tally-rental-price">
            {selections.rental ? formatCents(rentalTotal) : '—'}
          </span>
        </div>
      </div>

      <hr className="tally-divider" />

      <div className="tally-total-row">
        <span className="tally-total-label">Grand Total:</span>
        <span className="tally-total-value" data-testid="tally-grand-total">
          {formatCents(grandTotal)}
        </span>
      </div>

      {hasBudget && (
        <div className="tally-budget-section">
          <div className="tally-budget-row">
            <span>Overall Budget:</span>
            <span data-testid="tally-budget-total">{formatCents(budgetCents)}</span>
          </div>
          {isOverBudget ? (
            <div className="tally-budget-row tally-overage" role="alert">
              <span>Budget Overage:</span>
              <span data-testid="tally-overage">{formatCents(overageCents)}</span>
            </div>
          ) : (
            <div className="tally-budget-row tally-remaining">
              <span>Remaining Budget:</span>
              <span data-testid="tally-remaining">{formatCents(remainingCents)}</span>
            </div>
          )}
        </div>
      )}
    </section>
  );
}
