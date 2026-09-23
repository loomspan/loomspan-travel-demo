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
  }, [tripId, refreshKey]);

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
      </section>
    );
  }

  if (bookings.length === 0) {
    return null;
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
                      {booking.status}
                    </span>
                    <strong id={`history-ref-${booking.id}`} className="ref-code">
                      {booking.bookingReference}
                    </strong>
                  </div>
                  <strong>{formatCents(booking.grandTotalCents)}</strong>
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
                      <strong>Rental:</strong> {sel.rental.vehicleClassName} at{' '}
                      {sel.rental.locationName}{' '}
                      {booking.rentalReference && (
                        <span className="ref-code">({booking.rentalReference})</span>
                      )}
                    </div>
                  )}
                </div>
              </article>
            );
          })}
        </div>
      </details>
    </section>
  );
}
