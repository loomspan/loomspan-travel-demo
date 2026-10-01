import {render, screen} from '@testing-library/react';
import {expect, it} from 'vitest';
import {focusTripDialogFallback} from './tripDialogFocus';

it('restores to the visible trip section before hidden workspace and generic About headings', () => {
  render(<>
    <section hidden><h2 tabIndex={-1}>About this demo</h2></section>
    <div hidden><h1 id="workspace-heading" tabIndex={-1}>Mounted Working plan</h1></div>
    <h1 id="trips-heading" tabIndex={-1}>My Trips</h1>
    <h2 id="past-trips-heading" tabIndex={-1}>Past trips</h2>
  </>);
  focusTripDialogFallback();
  expect(screen.getByRole('heading', {name: 'Past trips'})).toHaveFocus();
});
