import {describe, expect, it} from 'vitest';
import {suggestedTripName, tripBaseName} from './tripName';

describe('Trip name suggestions', () => {
  it('uses city and dates without a suffix for the first Trip', () => {
    const base = tripBaseName('destination-sfo', '2027-03-10', '2027-03-14');
    expect(suggestedTripName(base)).toBe('San Francisco — Mar 10–14, 2027');
  });

  it('uses the first available letter only when the name is taken', () => {
    const base = tripBaseName('destination-muc', '2027-03-10', '2027-03-14');
    expect(suggestedTripName(base, [base, `${base} - A`, `${base} - C`])).toBe(`${base} - B`);
    expect(suggestedTripName(base, [base.toUpperCase()])).toBe(`${base} - A`);
  });
});

it.each([
  ['2027-03-13', '2027-03-20', 'Mar 13–20, 2027'],
  ['2027-03-28', '2027-04-03', 'Mar 28 – Apr 3, 2027'],
  ['2027-12-28', '2028-01-03', 'Dec 28, 2027 – Jan 3, 2028'],
  ['2027-03-03', '2027-03-06', 'Mar 3–6, 2027'],
  ['2027-03-13', '2027-03-13', 'Mar 13, 2027'],
])('formats %s through %s as %s', (start, end, dates) => {
  expect(tripBaseName('destination-sfo', start, end)).toBe(`San Francisco — ${dates}`);
});