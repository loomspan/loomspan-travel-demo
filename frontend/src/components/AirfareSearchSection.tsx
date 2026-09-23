import {useEffect, useState} from 'react';
import {
  tripsApi,
  type TripResponse,
  type FlightCombinationResponse,
  type AirfareSort,
} from '../api/tripsApi';
import {formatCents} from './ItinerarySummaryTally';
import {FlightSchedule} from './FlightSchedule';

type AirfareSearchSectionProps = {
  trip: TripResponse;
  draftId: string;
  onSelect: (option: FlightCombinationResponse) => Promise<void>;
  onCancel: () => void;
  pending: boolean;
};

export function formatMinutes(minutes: number): string {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return `${h}h ${m}m`;
}

export function AirfareSearchSection({
  trip,
  draftId,
  onSelect,
  onCancel,
  pending,
}: AirfareSearchSectionProps) {
  const [directOnly, setDirectOnly] = useState(false);
  const [sort, setSort] = useState<AirfareSort>('DEFAULT');
  const [options, setOptions] = useState<FlightCombinationResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | undefined>();
  const [retryKey, setRetryKey] = useState(0);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(undefined);

    tripsApi
      .searchAirfare(trip.id, draftId, {directOnly, sort})
      .then((res) => {
        if (active) {
          setOptions(res.options || []);
          setLoading(false);
        }
      })
      .catch((err) => {
        if (active) {
          setError(err instanceof Error ? err.message : 'Could not load flights.');
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [trip.id, draftId, directOnly, sort, retryKey]);

  return (
    <div className="component-search-section airfare-search" aria-labelledby="airfare-search-heading">
      <div className="search-header">
        <h4 id="airfare-search-heading">Search flights</h4>
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
        <div className="field-checkbox">
          <label htmlFor="airfare-direct-only">
            <input
              type="checkbox"
              id="airfare-direct-only"
              checked={directOnly}
              onChange={(e) => setDirectOnly(e.target.checked)}
            />
            Direct flights only
          </label>
        </div>

        <div className="field">
          <label htmlFor="airfare-sort">Sort by</label>
          <select
            id="airfare-sort"
            value={sort}
            onChange={(e) => setSort(e.target.value as AirfareSort)}
          >
            <option value="DEFAULT">Best match</option>
            <option value="LOWEST_PRICE">Lowest price</option>
            <option value="SHORTEST_DURATION">Shortest duration</option>
            <option value="EARLIEST_DEPARTURE">Earliest departure</option>
            <option value="FEWEST_STOPS">Fewest stops</option>
          </select>
        </div>
      </div>

      {loading && <p className="hint" role="status">Searching flights…</p>}
      {error && <div role="alert"><p className="field-error">{error} Retry the flight search.</p><button type="button" onClick={() => setRetryKey((value) => value + 1)}>Retry flight search</button></div>}

      {!loading && !error && options.length === 0 && (
        <p className="hint" role="status">No flights found matching your criteria. Change the filters and search again.</p>
      )}

      {!loading && !error && options.length > 0 && (
        <div className="flight-options-list">
          {options.map((opt) => (
            <article key={opt.combinationKey} className="card flight-option-card">
              <div className="flight-legs">
                <div className="flight-leg">
                  <span className="leg-badge">Outbound</span>
                  <div className="flight-leg-details">
                    <p className="flight-carrier">
                      {opt.outbound.carrier} • #{opt.outbound.flightNumber}
                    </p>
                    <FlightSchedule departureAirport={opt.outbound.originAirportCode} departureTime={opt.outbound.departureTime} departureTimeZone={opt.outbound.departureTimeZone} arrivalAirport={opt.outbound.destinationAirportCode} arrivalTime={opt.outbound.arrivalTime} arrivalTimeZone={opt.outbound.arrivalTimeZone} />
                    <p className="flight-subtext">
                      {formatMinutes(opt.outbound.durationMinutes)} •{' '}
                      {opt.outbound.stopCount === 0
                        ? 'Nonstop'
                        : `${opt.outbound.stopCount} stop${opt.outbound.stopCount > 1 ? 's' : ''} (${opt.outbound.layover?.airportCode ?? ''} ${opt.outbound.layover ? formatMinutes(opt.outbound.layover.durationMinutes) : ''})`}
                    </p>
                  </div>
                </div>

                <div className="flight-leg">
                  <span className="leg-badge">Return</span>
                  <div className="flight-leg-details">
                    <p className="flight-carrier">
                      {opt.returnFlight.carrier} • #{opt.returnFlight.flightNumber}
                    </p>
                    <FlightSchedule departureAirport={opt.returnFlight.originAirportCode} departureTime={opt.returnFlight.departureTime} departureTimeZone={opt.returnFlight.departureTimeZone} arrivalAirport={opt.returnFlight.destinationAirportCode} arrivalTime={opt.returnFlight.arrivalTime} arrivalTimeZone={opt.returnFlight.arrivalTimeZone} />
                    <p className="flight-subtext">
                      {formatMinutes(opt.returnFlight.durationMinutes)} •{' '}
                      {opt.returnFlight.stopCount === 0
                        ? 'Nonstop'
                        : `${opt.returnFlight.stopCount} stop${opt.returnFlight.stopCount > 1 ? 's' : ''} (${opt.returnFlight.layover?.airportCode ?? ''} ${opt.returnFlight.layover ? formatMinutes(opt.returnFlight.layover.durationMinutes) : ''})`}
                    </p>
                  </div>
                </div>
              </div>

              <div className="flight-pricing-action">
                <div className="flight-price-box">
                  <span className="flight-price-total">
                    {formatCents(opt.pricing.partyTotalPriceCents)}
                  </span>
                  <span className="flight-price-caption">
                    Party total ({opt.pricing.travelerCount} traveler{opt.pricing.travelerCount === 1 ? '' : 's'})
                  </span>
                </div>
                <button
                  type="button"
                  className="primary"
                  onClick={() => void onSelect(opt)}
                  disabled={pending}
                >
                  Select flight
                </button>
              </div>
            </article>
          ))}
        </div>
      )}
    </div>
  );
}
