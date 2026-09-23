import {render, screen} from '@testing-library/react';
import {describe, expect, it} from 'vitest';
import {FlightSchedule} from './FlightSchedule';

describe('FlightSchedule', () => {
  it('shows destination-local arrival date when a flight crosses midnight', () => {
    render(<FlightSchedule departureAirport="PDX" departureTime="2027-03-05T23:00:00Z" departureTimeZone="America/Los_Angeles" arrivalAirport="MUC" arrivalTime="2027-03-06T18:00:00Z" arrivalTimeZone="Europe/Berlin" />);
    expect(screen.getByText(/Departure: PDX.*March 5, 2027.*America\/Los_Angeles/)).toBeInTheDocument();
    expect(screen.getByText(/Arrival: MUC.*March 6, 2027.*Europe\/Berlin/)).toBeInTheDocument();
  });

  it('uses Mexico City local time and reverses endpoints on the return leg', () => {
    render(<FlightSchedule departureAirport="MEX" departureTime="2027-03-08T02:30:00Z" departureTimeZone="America/Mexico_City" arrivalAirport="PDX" arrivalTime="2027-03-08T09:30:00Z" arrivalTimeZone="America/Los_Angeles" />);
    expect(screen.getByText(/Departure: MEX.*March 7, 2027, 8:30 PM.*America\/Mexico_City/)).toBeInTheDocument();
    expect(screen.getByText(/Arrival: PDX.*March 8, 2027, 1:30 AM.*America\/Los_Angeles/)).toBeInTheDocument();
  });

  it('does not guess a schedule from incomplete legacy snapshots', () => {
    render(<FlightSchedule departureAirport="PDX" departureTime="invalid" departureTimeZone="America/Los_Angeles" arrivalAirport="SFO" arrivalTime={null} arrivalTimeZone="America/Los_Angeles" />);
    expect(screen.getByText(/Departure: PDX.*Schedule unavailable/)).toBeInTheDocument();
    expect(screen.getByText(/Arrival: SFO.*Schedule unavailable/)).toBeInTheDocument();
    expect(screen.queryByText(/Invalid Date/)).not.toBeInTheDocument();
  });
});
