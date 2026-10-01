import {describe, expect, it} from 'vitest';
import {suggestedTripName, tripBaseName} from './tripName';

describe('Trip name suggestions', () => {
  it('uses city and dates without a suffix for the first Trip', () => {
    const base = tripBaseName('destination-sfo', '2027-03-10', '2027-03-14');
    expect(suggestedTripName(base)).toBe('San Francisco - 2027-03-10 to 2027-03-14');
  });

  it('uses the first available letter only when the name is taken', () => {
    const base = tripBaseName('destination-muc', '2027-03-10', '2027-03-14');
    expect(suggestedTripName(base, [base, `${base} - A`, `${base} - C`])).toBe(`${base} - B`);
    expect(suggestedTripName(base, [base.toUpperCase()])).toBe(`${base} - A`);
  });
});
