import {useEffect, useState} from 'react';
import {
  tripsApi,
  type TripResponse,
  type StayOptionResponse,
  type StaySort,
  type AccommodationType,
} from '../api/tripsApi';
import {formatCents} from './ItinerarySummaryTally';

type StaySearchSectionProps = {
  trip: TripResponse;
  draftId: string;
  initialType?: AccommodationType;
  onSelect: (option: StayOptionResponse) => Promise<void>;
  onCancel: () => void;
  pending: boolean;
};

export function StaySearchSection({
  trip,
  draftId,
  initialType = 'HOTEL',
  onSelect,
  onCancel,
  pending,
}: StaySearchSectionProps) {
  const [type, setType] = useState<AccommodationType>(initialType);
  const [sort, setSort] = useState<StaySort>('DEFAULT');
  const [options, setOptions] = useState<StayOptionResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | undefined>();
  const [retryKey, setRetryKey] = useState(0);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(undefined);

    tripsApi
      .searchStays(trip.id, draftId, {type, sort})
      .then((res) => {
        if (active) {
          setOptions(res.options || []);
          setLoading(false);
        }
      })
      .catch((err) => {
        if (active) {
          setError(err instanceof Error ? err.message : 'Could not load stays.');
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [trip.id, draftId, type, sort, retryKey]);

  return (
    <div className="component-search-section stay-search" aria-labelledby="stay-search-heading">
      <div className="search-header">
        <h4 id="stay-search-heading">Search stays</h4>
        <button
          type="button"
          className="text-button"
          onClick={onCancel}
          disabled={pending}
        >
          Cancel
        </button>
      </div>

      <div className="search-controls field-group">
        <div className="field">
          <label htmlFor="stay-accommodation-type">Accommodation type</label>
          <select
            id="stay-accommodation-type"
            value={type}
            onChange={(e) => setType(e.target.value as AccommodationType)}
          >
            <option value="HOTEL">Hotel</option>
            <option value="BED_AND_BREAKFAST">Bed &amp; Breakfast</option>
            <option value="VACATION_RENTAL">Vacation Rental</option>
          </select>
        </div>

        <div className="field">
          <label htmlFor="stay-sort">Sort by</label>
          <select
            id="stay-sort"
            value={sort}
            onChange={(e) => setSort(e.target.value as StaySort)}
          >
            <option value="DEFAULT">Best match</option>
            <option value="LOWEST_PRICE">Lowest price</option>
            <option value="HIGHEST_RATING">Highest rating</option>
            <option value="NEAREST_CITY_CENTER">Nearest city center</option>
          </select>
        </div>
      </div>

      {loading && <p className="hint" role="status">Searching accommodations…</p>}
      {error && <div role="alert"><p className="field-error">{error} Retry the accommodation search.</p><button type="button" onClick={() => setRetryKey((value) => value + 1)}>Retry stay search</button></div>}

      {!loading && !error && options.length === 0 && (
        <p className="hint">No accommodations found matching your criteria.</p>
      )}

      {!loading && !error && options.length > 0 && (
        <div className="stay-options-list">
          {options.map((opt) => (
            <article key={opt.accommodationUnitId} className="card stay-option-card">
              <div className="stay-main-info">
                <div className="stay-headline">
                  <h5 className="stay-title">{opt.propertyName}</h5>
                  <span className="stay-unit-name">{opt.unitName}</span>
                </div>

                <div className="stay-meta-row">
                  <span className="stay-rating" data-testid="stay-rating">
                    ★ {opt.guestRating} / 5
                  </span>
                  <span className="stay-distance" data-testid="stay-distance">
                    {(opt.distanceToCityCenterMeters / 1000).toFixed(1)} km to city center
                  </span>
                  <span className="stay-rooms" data-testid="stay-rooms">
                    {opt.pricing.requiredRooms} room{opt.pricing.requiredRooms === 1 ? '' : 's'}
                  </span>
                </div>

                <p className="stay-pricing-breakdown">
                  {opt.pricing.nightCount} night{opt.pricing.nightCount === 1 ? '' : 's'} •{' '}
                  {formatCents(opt.pricing.perRoomTotalPriceCents)} per room total
                </p>

                {opt.fitsBudget !== null && opt.fitsBudget !== undefined && (
                  <span
                    className={`badge ${opt.fitsBudget ? 'badge-success' : 'badge-warning'}`}
                  >
                    {opt.fitsBudget ? 'Fits budget' : 'Over budget'}
                  </span>
                )}
              </div>

              <div className="stay-pricing-action">
                <div className="stay-price-box">
                  <span className="stay-price-total">
                    {formatCents(opt.pricing.totalPriceCents)}
                  </span>
                  <span className="stay-price-caption">
                    Total stay ({opt.pricing.requiredRooms} room{opt.pricing.requiredRooms === 1 ? '' : 's'}, {opt.pricing.nightCount} night{opt.pricing.nightCount === 1 ? '' : 's'})
                  </span>
                </div>
                <button
                  type="button"
                  className="primary"
                  onClick={() => void onSelect(opt)}
                  disabled={pending}
                >
                  Select stay
                </button>
              </div>
            </article>
          ))}
        </div>
      )}
    </div>
  );
}
