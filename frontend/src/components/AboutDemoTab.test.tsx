import {expect, it} from 'vitest';
import {render, screen, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {AboutDemoTab} from './AboutDemoTab';

it('discloses the complete demo scope and lets keyboard users return to the page', async () => {
  const user = userEvent.setup();
  render(<><AboutDemoTab /><button type="button">Continue planning</button></>);
  const trigger = screen.getByRole('button', {name: 'About this demo'});

  expect(trigger).toHaveAttribute('aria-expanded', 'false');
  expect(screen.queryByRole('region', {name: 'About this demo'})).not.toBeInTheDocument();

  await user.tab();
  expect(trigger).toHaveFocus();
  await user.keyboard('{Enter}');
  const panel = screen.getByRole('region', {name: 'About this demo'});
  expect(trigger).toHaveAttribute('aria-expanded', 'true');
  expect(within(panel).getByRole('heading', {name: 'About this demo'})).toHaveFocus();
  expect(panel).toHaveTextContent('Suppliers, schedules, prices, availability, and bookings are fictional');
  expect(panel).toHaveTextContent('No payment or real reservation occurs');
  expect(panel).toHaveTextContent('PDX');
  expect(panel).toHaveTextContent('San Francisco, Munich, or Mexico City');
  expect(panel).toHaveTextContent('March 2027');
  expect(panel).toHaveTextContent('Start with Plan Trip, Airfare, or Stay');
  expect(panel).toHaveTextContent('Add other components explicitly');
  expect(panel).toHaveTextContent('save a Draft as Planned');
  expect(panel).toHaveTextContent('compare up to three Planned alternatives');
  expect(panel).toHaveTextContent('review one to book');
  expect(panel).toHaveTextContent('cancel an active Booking only before its departure date begins');
  expect(panel).toHaveTextContent('America/Los_Angeles');

  await user.tab();
  expect(within(panel).getByRole('button', {name: 'Close About this demo'})).toHaveFocus();
  await user.tab();
  expect(screen.getByRole('button', {name: 'Continue planning'})).toHaveFocus();
  await user.tab({shift: true});
  await user.keyboard('{Escape}');
  expect(trigger).toHaveFocus();
  expect(trigger).toHaveAttribute('aria-expanded', 'false');
  expect(screen.queryByRole('region', {name: 'About this demo'})).not.toBeInTheDocument();

  await user.keyboard('{Enter}');
  await user.click(within(screen.getByRole('region', {name: 'About this demo'})).getByRole('button', {name: 'Close About this demo'}));
  expect(trigger).toHaveFocus();
});
