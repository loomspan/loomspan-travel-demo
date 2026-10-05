import type {AirfareComponentResponse, StayComponentResponse, RentalComponentResponse, FlightCombinationResponse, StayOptionResponse} from '../api/tripsApi';
import {formatCents} from './ItinerarySummaryTally';

export const validCents = (n: unknown): n is number => typeof n === 'number' && Number.isSafeInteger(n) && n >= 0;
const sum = (values: unknown[]): number | undefined => values.every(validCents) ? values.reduce<number>((a, b) => a + (b as number), 0) : undefined;
export function flightTotal(a: AirfareComponentResponse | null | undefined, party: number): number | undefined {
  if (!a || !Number.isInteger(party) || party < 1) return undefined;
  const total = sum([a.outboundBaseFareCents, a.outboundTaxCents, a.outboundFeeCents, a.returnBaseFareCents, a.returnTaxCents, a.returnFeeCents]);
  return total === undefined ? undefined : total * party;
}
export function stayTotal(s: StayComponentResponse | null | undefined): number | undefined {
  if (!s || !s.nights?.length || !Number.isInteger(s.unitCount) || s.unitCount < 1) return undefined;
  const total = sum(s.nights.flatMap(n => [n.basePriceCents, n.taxCents, n.feeCents]));
  return total === undefined ? undefined : total * s.unitCount;
}
export function rentalCycles(pickup: string, returned: string): number | undefined {
  const elapsed = Date.parse(returned) - Date.parse(pickup);
  return Number.isFinite(elapsed) && elapsed > 0 ? Math.ceil(elapsed / 86400000) : undefined;
}
export function rentalTotal(r: RentalComponentResponse | null | undefined): number | undefined {
  if (!r) return undefined;
  const daily = sum([r.dailyBasePriceCents, r.dailyTaxCents, r.dailyFeeCents]), cycles = rentalCycles(r.pickupAt, r.returnAt);
  return daily === undefined || cycles === undefined ? undefined : daily * cycles;
}
export function sameRental(r: RentalComponentResponse | null | undefined, id: number, pickup: string, returned: string): boolean {
  return Boolean(r && r.rentalUnitId === id && rentalCycles(pickup, returned) && Date.parse(r.pickupAt) === Date.parse(pickup) && Date.parse(r.returnAt) === Date.parse(returned));
}
export function priceDifference(current: unknown, next: unknown): string {
  if (!validCents(current) || !validCents(next)) return 'Comparison unavailable';
  const difference = next - current;
  return difference === 0 ? 'Same price as current selection' : `${formatCents(Math.abs(difference))} ${difference > 0 ? 'more' : 'less'} than current selection`;
}
function localDate(instant: string | null | undefined, zone: string | null | undefined): string | undefined {
  if (!instant || !zone) return undefined;
  try { return new Intl.DateTimeFormat('en-CA', {timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit'}).format(new Date(instant)); } catch {return undefined;}
}
export function flightComparable(a: AirfareComponentResponse | FlightCombinationResponse | null | undefined, option: FlightCombinationResponse, start: string, end: string, party: number): boolean {
  if (!a || option.pricing.travelerCount !== party) return false;
  if (localDate(option.outbound?.departureTime, option.outbound?.departureTimeZone) !== start || localDate(option.returnFlight?.departureTime, option.returnFlight?.departureTimeZone) !== end) return false;
  if ('outbound' in a) return a.pricing.travelerCount === party && localDate(a.outbound.departureTime, a.outbound.departureTimeZone) === start && localDate(a.returnFlight.departureTime, a.returnFlight.departureTimeZone) === end;
  return localDate(a.outboundDepartureTime, a.outboundDepartureTimeZone) === start && localDate(a.returnDepartureTime, a.returnDepartureTimeZone) === end;
}
export function stayComparable(s: StayComponentResponse | StayOptionResponse | null | undefined, option: StayOptionResponse, start: string, end: string): boolean {
  if (!s) return false;
  const nights = 'pricing' in s ? s.pricing.nights : s.nights;
  const rooms = 'pricing' in s ? s.pricing.requiredRooms : s.unitCount;
  const count = (Date.parse(end) - Date.parse(start)) / 86400000;
  const matches = (values: typeof nights) => values?.length === count && values.every((n, i) => n.date === new Date(Date.parse(start) + i * 86400000).toISOString().slice(0, 10));
  return Boolean(rooms === option.pricing.requiredRooms && Number.isInteger(count) && count > 0 && option.pricing.nightCount === count && matches(nights) && matches(option.pricing.nights));
}
