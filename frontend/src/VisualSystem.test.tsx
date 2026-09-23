import {render, screen} from '@testing-library/react';
import {describe, expect, it, vi} from 'vitest';
import {AuthScreen} from './components/AuthScreen';
import {ItinerarySummaryTally} from './components/ItinerarySummaryTally';
import type {DraftSelectionResponse, TripResponse} from './api/tripsApi';

describe('DeTour visual language', () => {
  it('shows the text wordmark on the public screen', () => {
    render(<AuthScreen onLogin={vi.fn()} onRegister={vi.fn()} onFailure={vi.fn()} />);
    expect(screen.getByText('DeTour')).toHaveClass('wordmark');
  });

  it('distinguishes unselected components from zero cost and names USD amounts', () => {
    const trip = {travelerCount: 1, budgetCents: 0} as TripResponse;
    const selections = {airfare: null, stay: null, rental: null} as DraftSelectionResponse;
    render(<ItinerarySummaryTally trip={trip} selections={selections} />);
    expect(screen.getByTestId('tally-airfare-price')).toHaveTextContent('Not selected');
    expect(screen.getByTestId('tally-grand-total')).toHaveTextContent('$0.00 USD');
    expect(screen.getByTestId('tally-budget-total')).toHaveTextContent('$0.00 USD');
  });
});
