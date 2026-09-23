import {useEffect, useState} from 'react';
import {
  tripsApi,
  type TripResponse,
  type RentalOptionResponse,
  type RentalSort,
} from '../api/tripsApi';
import {formatCents} from './ItinerarySummaryTally';

type RentalSearchSectionProps = {
  trip: TripResponse;
  draftId: string;
  onSelect: (option: RentalOptionResponse, pickupAtIso: string, returnAtIso: string) => Promise<void>;
  onCancel: () => void;
  pending: boolean;
};

export function isDriverEligible(travelerAges?: number[] | null): boolean {
  if (!travelerAges || travelerAges.length === 0) return false;
  return travelerAges.some((age) => age >= 25);
}

export const DESTINATION_TIMEZONES: Record<string, string> = {
  'destination-sfo': 'America/Los_Angeles',
  'destination-muc': 'Europe/Berlin',
  'destination-mex': 'America/Mexico_City',
};

export const DRIVER_AGE_EXPLANATION =
  'Rental cars require at least one traveler aged 25 or older. Please add traveler ages in Trip Details to select a car.';

export function formatLocalToDestinationIso(localDateTimeStr: string, destinationKey: string): string {
  if (!localDateTimeStr) return '';
  if (
    localDateTimeStr.includes('Z') ||
    localDateTimeStr.includes('+') ||
    (localDateTimeStr.length > 19 && localDateTimeStr.slice(10).includes('-'))
  ) {
    return localDateTimeStr;
  }
  const timeZone = DESTINATION_TIMEZONES[destinationKey] || 'UTC';
  const cleanStr = localDateTimeStr.length === 16 ? `${localDateTimeStr}:00` : localDateTimeStr;
  try {
    const parts = new Intl.DateTimeFormat('en-US', {
      timeZone,
      timeZoneName: 'longOffset',
    }).formatToParts(new Date(`${cleanStr}Z`));
    const offsetPart = parts.find((p) => p.type === 'timeZoneName');
    if (offsetPart && offsetPart.value.startsWith('GMT')) {
      const offset = offsetPart.value.replace('GMT', '');
      return `${cleanStr}${offset === '' ? 'Z' : offset}`;
    }
  } catch {
    // fallback
  }
  return `${cleanStr}Z`;
}

export function RentalSearchSection({
  trip,
  draftId,
  onSelect,
  onCancel,
  pending,
}: RentalSearchSectionProps) {
  const [pickupAt, setPickupAt] = useState(`${trip.startDate}T10:00`);
  const [returnAt, setReturnAt] = useState(`${trip.endDate}T10:00`);
  const [sort, setSort] = useState<RentalSort>('DEFAULT');
  const [options, setOptions] = useState<RentalOptionResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | undefined>();
  const [retryKey, setRetryKey] = useState(0);
  const [dateError, setDateError] = useState<string | undefined>();
  const [serverExplanation, setServerExplanation] = useState<string | null>(null);

  const driverEligible = isDriverEligible(trip.travelerAges);

  useEffect(() => {
    // Validate pickup and return dates
    if (!pickupAt || !returnAt) {
      setDateError('Please provide both pickup and return date and time.');
      setOptions([]);
      setLoading(false);
      return;
    }

    if (returnAt <= pickupAt) {
      setDateError('Return date and time must be after pickup date and time.');
      setOptions([]);
      setLoading(false);
      return;
    }

    const pickupDate = pickupAt.split('T')[0];
    const returnDate = returnAt.split('T')[0];
    if (pickupDate < trip.startDate || returnDate > trip.endDate) {
      setDateError(`Rental period must be within trip dates (${trip.startDate} to ${trip.endDate}).`);
      setOptions([]);
      setLoading(false);
      return;
    }

    setDateError(undefined);
    let active = true;
    setLoading(true);
    setError(undefined);

    const pickupIso = formatLocalToDestinationIso(pickupAt, trip.destinationKey);
    const returnIso = formatLocalToDestinationIso(returnAt, trip.destinationKey);

    tripsApi
      .searchRentals(trip.id, draftId, {
        pickupAt: pickupIso,
        returnAt: returnIso,
        sort,
      })
      .then((res) => {
        if (active) {
          setOptions(res.options || []);
          if (res.explanation) {
            setServerExplanation(res.explanation);
          }
          setLoading(false);
        }
      })
      .catch((err) => {
        if (active) {
          setError(err instanceof Error ? err.message : 'Could not load rental cars.');
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [trip.id, trip.startDate, trip.endDate, trip.destinationKey, draftId, pickupAt, returnAt, sort, retryKey]);

  return (
    <div className="component-search-section rental-search" aria-labelledby="rental-search-heading">
      <div className="search-header">
        <h4 id="rental-search-heading">Search rental cars</h4>
        <button
          type="button"
          className="text-button"
          onClick={onCancel}
          disabled={pending}
        >
          Cancel
        </button>
      </div>

      {!driverEligible && (
        <div
          className="driver-age-notice"
          role="alert"
          style={{marginBottom: '1rem', color: '#c53030', backgroundColor: '#fff5f5', padding: '0.75rem', borderRadius: '4px', border: '1px solid #feb2b2'}}
        >
          {serverExplanation || DRIVER_AGE_EXPLANATION}
        </div>
      )}

      <div className="search-controls field-group">
        <div className="field">
          <label htmlFor="rental-pickup-at">Pickup date &amp; time</label>
          <input
            type="datetime-local"
            id="rental-pickup-at"
            value={pickupAt}
            onChange={(e) => setPickupAt(e.target.value)}
          />
        </div>

        <div className="field">
          <label htmlFor="rental-return-at">Return date &amp; time</label>
          <input
            type="datetime-local"
            id="rental-return-at"
            value={returnAt}
            onChange={(e) => setReturnAt(e.target.value)}
          />
        </div>

        <div className="field">
          <label htmlFor="rental-sort">Sort by</label>
          <select
            id="rental-sort"
            value={sort}
            onChange={(e) => setSort(e.target.value as RentalSort)}
          >
            <option value="DEFAULT">Best match</option>
            <option value="LOWEST_PRICE">Lowest price</option>
          </select>
        </div>
      </div>

      {dateError && <p className="field-error" role="alert">{dateError}</p>}
      {loading && <p className="hint" role="status">Searching cars…</p>}
      {error && <div role="alert"><p className="field-error">{error}</p><button type="button" onClick={() => setRetryKey((value) => value + 1)}>Retry rental search</button></div>}

      {!loading && !error && !dateError && options.length === 0 && (
        <p className="hint">No rental cars found matching your criteria.</p>
      )}

      {!loading && !error && !dateError && options.length > 0 && (
        <div className="rental-options-list">
          {options.map((opt) => (
            <article key={opt.rentalUnitId} className="card rental-option-card">
              <div className="rental-main-info">
                <div className="rental-headline">
                  <h5 className="rental-vehicle-class">{opt.vehicleClassName}</h5>
                  <span className="rental-location">
                    {opt.locationName} ({opt.airportIataCode})
                  </span>
                </div>

                <p className="rental-billing-cycles">
                  {opt.pricing.billingCycles} billing cycle{opt.pricing.billingCycles === 1 ? '' : 's'} (consecutive 24-hr periods) •{' '}
                  {formatCents(opt.pricing.dailyTotalPriceCents)} / day
                </p>

                {opt.fitsBudget !== null && opt.fitsBudget !== undefined && (
                  <span
                    className={`badge ${opt.fitsBudget ? 'badge-success' : 'badge-warning'}`}
                  >
                    {opt.fitsBudget ? 'Fits budget' : 'Over budget'}
                  </span>
                )}
              </div>

              <div className="rental-pricing-action">
                <div className="rental-price-box">
                  <span className="rental-price-total">
                    {formatCents(opt.pricing.totalPriceCents)}
                  </span>
                  <span className="rental-price-caption">
                    Total car rental (taxes &amp; fees included)
                  </span>
                </div>
                <button
                  type="button"
                  className="primary"
                  onClick={() =>
                    void onSelect(
                      opt,
                      formatLocalToDestinationIso(pickupAt, trip.destinationKey),
                      formatLocalToDestinationIso(returnAt, trip.destinationKey)
                    )
                  }
                  disabled={!driverEligible || pending}
                  title={
                    !driverEligible
                      ? 'Rental cars require at least one traveler aged 25 or older.'
                      : undefined
                  }
                >
                  Select car
                </button>
              </div>
            </article>
          ))}
        </div>
      )}
    </div>
  );
}
