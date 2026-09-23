type FlightScheduleProps = {
  departureAirport?: string | null;
  departureTime?: string | null;
  departureTimeZone?: string | null;
  arrivalAirport?: string | null;
  arrivalTime?: string | null;
  arrivalTimeZone?: string | null;
};

export function formatFlightEndpoint(time?: string | null, timeZone?: string | null): string {
  const unavailable = `Schedule unavailable${timeZone ? ` (${timeZone})` : ''}`;
  if (!time || !timeZone) return unavailable;
  const instant = new Date(time);
  if (Number.isNaN(instant.getTime())) return unavailable;
  try {
    const date = new Intl.DateTimeFormat('en-US', {
      month: 'long', day: 'numeric', year: 'numeric',
      timeZone,
    }).format(instant);
    const clock = new Intl.DateTimeFormat('en-US', {
      hour: 'numeric', minute: '2-digit', hour12: true,
      timeZone,
    }).format(instant);
    return `${date}, ${clock} (${timeZone})`;
  } catch {
    return unavailable;
  }
}

export function FlightSchedule({
  departureAirport, departureTime, departureTimeZone,
  arrivalAirport, arrivalTime, arrivalTimeZone,
}: FlightScheduleProps) {
  return (
    <div className="flight-schedule">
      <div>Departure: {departureAirport || 'Airport unavailable'} — {formatFlightEndpoint(departureTime, departureTimeZone)}</div>
      <div>Arrival: {arrivalAirport || 'Airport unavailable'} — {formatFlightEndpoint(arrivalTime, arrivalTimeZone)}</div>
    </div>
  );
}
