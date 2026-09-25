import {render, screen} from '@testing-library/react';
import {describe, expect, it, vi} from 'vitest';
// TypeScript's browser config omits Node declarations used only by this test.
// @ts-expect-error Node's fs types are not part of the browser build.
import {readFileSync} from 'node:fs';
import {AuthScreen} from './components/AuthScreen';
import {ActionIcon} from './components/ActionIcon';
import {ItinerarySummaryTally, formatTallyCents} from './components/ItinerarySummaryTally';
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

  it('keeps a missing server tally distinct from a selected zero-price component', () => {
    expect(formatTallyCents(undefined)).toBe('Total unavailable');
    expect(formatTallyCents(0)).toBe('$0.00');
  });

  it('disables animated movement for reduced motion without removing focus outlines', () => {
    const css = readFileSync('src/style.css', 'utf8');
    expect(css).toMatch(/@media \(prefers-reduced-motion: reduce\)/);
    expect(css).toMatch(/scroll-behavior: auto !important/);
    expect(css).toMatch(/:focus-visible\s*\{[^}]*outline:/);
  });

  it('keeps native control states visible in standard and forced colors', () => {
    const css = readFileSync('src/style.css', 'utf8');
    expect(css).toMatch(/select:hover:not\(:disabled\)/);
    expect(css).toMatch(/select:focus-visible, input\[type="checkbox"\]:focus-visible/);
    expect(css).toMatch(/select:disabled/);
    expect(css).toMatch(/select\[aria-invalid="true"\], input\[type="checkbox"\]\[aria-invalid="true"\]/);
    expect(css).toMatch(/input\[type="checkbox"\]:checked/);
    expect(css).toMatch(/input\[type="checkbox"\]:disabled/);
    expect(css).toMatch(/@media \(forced-colors: active\)/);
  });

  it('treats action icons as decorative while text carries the action name', () => {
    render(<button type="button"><ActionIcon name="airfare" />Airfare</button>);
    expect(screen.getByRole('button', {name: 'Airfare'})).toBeInTheDocument();
    expect(document.querySelector('.action-icon')).toHaveAttribute('aria-hidden', 'true');
  });
});
