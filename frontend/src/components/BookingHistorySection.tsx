import {useEffect, useState} from 'react';
import {tripsApi, type BookingResponse} from '../api/tripsApi';
import {formatCents} from './ItinerarySummaryTally';

type BookingHistorySectionProps = {
  tripId: string;
  refreshKey?: number;
  initialBookings?: BookingResponse[];
};

export function BookingHistorySection({
  tripId,
  refreshKey = 0,
  initialBookings,
}: BookingHistorySectionProps) {
  const [bookings, setBookings] = useState<BookingResponse[]>(initialBookings ?? []);
  const [loading, setLoading] = useState<boolean>(!initialBookings);
  const [error, setError] = useState<string | undefined>();
  const [retryKey, setRetryKey] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(undefined);

    tripsApi
      .getBookingHistory(tripId)
      .then((data) => {
        if (!cancelled) {
          setBookings(data);
          setLoading(false);
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : 'Could not load booking history.');
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [tripId, refreshKey, retryKey]);

  if (loading && bookings.length === 0) {
    return (
      <section className="booking-history-section" aria-labelledby="booking-history-heading">
        <h3 id="booking-history-heading">Booking History</h3>
        <p className="hint">Loading booking history…</p>
      </section>
    );
  }

  if (error && bookings.length === 0) {
    return (
      <section className="booking-history-section" aria-labelledby="booking-history-heading">
        <h3 id="booking-history-heading">Booking History</h3>
        <p className="field-error" role="alert">{error}</p>
        <button type="button" onClick={() => setRetryKey((value) => value + 1)}>Retry booking history</button>
      </section>
    );
  }

  if (bookings.length === 0) {
    return <section className="booking-history-section" aria-labelledby="booking-history-heading"><h3 id="booking-history-heading">Booking History</h3><p>No booking history yet.</p></section>;
  }

  return (
    <section className="booking-history-section" aria-labelledby="booking-history-heading">
      <details className="booking-history-accordion" open>
        <summary className="booking-history-summary">
          <span id="booking-history-heading">Booking History ({bookings.length})</span>
          <span className="hint" style={{fontWeight: 'normal', fontSize: '0.85rem'}}>
            Immutable audit record
          </span>
        </summary>

        {error && <div role="alert"><p className="field-error">{error} Showing the last loaded booking history.</p><button type="button" onClick={() => setRetryKey((value) => value + 1)}>Retry booking history</button></div>}

        <div className="booking-history-list" role="list">
          {bookings.map((booking) => {
            const isCanceled = booking.status.toUpperCase() === 'CANCELED';
            const sel = booking.selections;

            return (
              <article
                key={booking.id}
                className="history-item"
                role="listitem"
                aria-labelledby={`history-ref-${booking.id}`}
              >
                <div className="history-item-header">
                  <div>
                    <span
                      className={`badge ${isCanceled ? 'badge-canceled' : 'badge-booked'}`}
                      style={{marginRight: '0.5rem'}}
                    >
                      {isCanceled ? 'Canceled Booking' : 'Booking'}
                    </span>
                    <strong id={`history-ref-${booking.id}`} className="ref-code">
                      {booking.bookingReference}
                    </strong>
                  </div>
                  <strong>Grand total: {formatCents(booking.grandTotalCents)} USD</strong>
                </div>

                <div className="history-meta">
                  <span>
                    Booked:{' '}
                    <strong>
                      {booking.bookedAt
                        ? new Date(booking.bookedAt).toLocaleDateString()
                        : 'Confirmed'}
                    </strong>
                  </span>
                  {isCanceled && booking.canceledAt && (
                    <span>
                      Canceled:{' '}
                      <strong>{new Date(booking.canceledAt).toLocaleDateString()}</strong>
                    </span>
                  )}
                </div>

                <div className="history-breakdown">
                  {sel?.airfare && (
                    <div>
                      <strong>Airfare:</strong> {sel.airfare.outboundDescription} /{' '}
                      {sel.airfare.returnDescription}{' '}
                      {booking.airfareReference && (
                        <span className="ref-code">({booking.airfareReference})</span>
                      )}
                    </div>
                  )}
                  {sel?.stay && (
                    <div>
                      <strong>Stay:</strong> {sel.stay.propertyName} ({sel.stay.unitName}){' '}
                      {booking.stayReference && (
                        <span className="ref-code">({booking.stayReference})</span>
                      )}
                    </div>
                  )}
                  {sel?.rental && (
                    <div>
                      <strong>Rental Car:</strong> {sel.rental.vehicleClassName} at{' '}
                      {sel.rental.locationName}{' '}
                      {booking.rentalReference && (
                        <span className="ref-code">({booking.rentalReference})</span>
                      )}
                    </div>
                  )}
                </div>
                <div className="alternative-costs" aria-label="Booking totals in USD">
                  <div><span>Airfare total</span><strong>{sel?.airfare ? formatCents(booking.tally.airfareTotalCents) : 'Not selected'}</strong></div>
                  <div><span>Stay total</span><strong>{sel?.stay ? formatCents(booking.tally.stayTotalCents) : 'Not selected'}</strong></div>
                  <div><span>Rental Car total</span><strong>{sel?.rental ? formatCents(booking.tally.rentalTotalCents) : 'Not selected'}</strong></div>
                  {booking.tally.isOverBudget && <div className="alternative-overage"><span>Budget overage</span><strong>{formatCents(booking.tally.budgetOverageCents ?? 0)} USD</strong></div>}
                  {!booking.tally.isOverBudget && booking.tally.remainingBudgetCents !== null && <div><span>Budget remaining</span><strong>{formatCents(booking.tally.remainingBudgetCents)} USD</strong></div>}
                </div>
              </article>
            );
          })}
        </div>
      </details>
    </section>
  );
}
