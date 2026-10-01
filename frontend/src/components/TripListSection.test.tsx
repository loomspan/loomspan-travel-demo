import {act, fireEvent, render, screen, within} from '@testing-library/react';
import {afterEach, expect, it, vi} from 'vitest';
import type {TripProfileSummary} from '../api/tripsApi';
import userEvent from '@testing-library/user-event';
import {IdentityApiError} from '../api/identityApi';
import {TripListSection} from './TripListSection';

const summary = (id: string, overrides: Partial<TripProfileSummary> = {}): TripProfileSummary => ({
  id, name: id, label: id, destinationKey: 'destination-sfo', destinationName: 'San Francisco',
  startDate: '2027-03-10', endDate: '2027-03-14', version: 0, status: 'ACTIVE', temporalStatus: 'UPCOMING',
  draftCount: 1, plannedCount: 0, expiredAlternativeCount: 0, bookedCount: 0, hasBookingHistory: false,
  alternatives: [], inProgress: false, ...overrides,
});

it('puts server-marked in-progress trips before future trips', () => {
  render(<TripListSection upcoming={[summary('Future'), summary('Current', {inProgress: true})]} past={[]}
    onSelectTrip={vi.fn()} onDeleteTrip={vi.fn()} headingLevel={2} />);
  const region = screen.getByRole('region', {name: 'Upcoming trips (2)'});
  expect(within(region).getAllByRole('article').map(card => within(card).getByRole('heading').textContent)).toEqual(['Current', 'Future']);
  expect(within(region).getByText('In progress')).toBeInTheDocument();
});


afterEach(() => vi.useRealTimers());
const headings = (name: string) => within(screen.getByRole('region', {name})).getAllByRole('article').map(card => within(card).getByRole('heading').textContent);
const props = {onSelectTrip: vi.fn(), onDeleteTrip: vi.fn(), headingLevel: 2 as const};

it('sorts immutable groups by their relevant date, name and stable public ID', () => {
  const upcoming = [
    summary('z', {name: 'alpha'}), summary('future-early', {startDate: '2027-03-09'}),
    summary('a', {name: 'Alpha'}), summary('current-late', {inProgress: true, startDate: '2027-03-08'}),
    summary('current-early', {inProgress: true, startDate: '2027-03-07'}),
    summary('cancel-late', {status: 'CANCELED', startDate: '2027-03-20', endDate: '2027-03-21', inProgress: true}),
  ];
  const past = [
    summary('past-old', {temporalStatus: 'PAST', startDate: '2027-03-09', endDate: '2027-03-10'}),
    summary('past-new', {temporalStatus: 'PAST', startDate: '2027-03-01', endDate: '2027-03-14'}),
    summary('cancel-early', {status: 'CANCELED', startDate: '2027-03-02', endDate: '2027-03-25'}),
  ];
  upcoming.forEach(Object.freeze); past.forEach(Object.freeze); Object.freeze(upcoming); Object.freeze(past);
  const view = render(<TripListSection {...props} upcoming={upcoming} past={past} />);
  expect(headings('Upcoming trips (5)')).toEqual(['current-early', 'current-late', 'future-early', 'Alpha', 'alpha']);
  expect(headings('Past trips (2)')).toEqual(['past-new', 'past-old']);
  expect(headings('Canceled trips (2)')).toEqual(['cancel-late', 'cancel-early']);
  expect(upcoming.map(trip => trip.id)).toEqual(['z', 'future-early', 'a', 'current-late', 'current-early', 'cancel-late']);
  view.rerender(<TripListSection {...props} upcoming={[...upcoming].reverse()} past={[...past].reverse()} />);
  expect(headings('Upcoming trips (5)')).toEqual(['current-early', 'current-late', 'future-early', 'Alpha', 'alpha']);
  expect(within(screen.getByRole('region', {name: 'Canceled trips (2)'})).queryByText('In progress')).not.toBeInTheDocument();
});

it('preserves all four counts, section order, filter state and empty hints', async () => {
  const user = userEvent.setup();
  const view = render(<TripListSection {...props} upcoming={[summary('Current', {inProgress: true}), summary('Cancelled', {status: 'CANCELED'})]}
    past={[summary('Past', {temporalStatus: 'PAST'}), summary('Cancelled past', {status: 'CANCELED'})]} />);
  expect(screen.getAllByRole('region').map(region => region.getAttribute('aria-labelledby'))).toEqual(['upcoming-trips-heading', 'past-trips-heading', 'canceled-trips-heading']);
  expect(screen.getByRole('button', {name: 'All trips (4)'})).toHaveAttribute('aria-pressed', 'true');
  await user.click(screen.getByRole('button', {name: 'Upcoming (1)'}));
  expect(screen.getAllByRole('article')).toHaveLength(1);
  expect(screen.getByText('In progress')).toBeInTheDocument();
  expect(screen.getByRole('button', {name: 'Upcoming (1)'})).toHaveAttribute('aria-pressed', 'true');
  await user.click(screen.getByRole('button', {name: 'Canceled (2)'}));
  expect(screen.getAllByRole('article')).toHaveLength(2);
  view.rerender(<TripListSection {...props} upcoming={[]} past={[]} />);
  expect(screen.getByText('No canceled trips.')).toBeInTheDocument();
  await user.click(screen.getByRole('button', {name: 'All trips (0)'}));
  expect(screen.getByText('No upcoming trips planned yet.')).toBeInTheDocument();
  expect(screen.getByText('No past trips.')).toBeInTheDocument();
  expect(screen.queryByRole('region', {name: /Canceled trips/})).not.toBeInTheDocument();
});

it('renders compact hierarchy, year-bearing date endpoints and server flags independently of browser time', async () => {
  vi.useFakeTimers(); vi.setSystemTime(new Date('2027-03-12T12:00:00Z'));
  const trip = summary('long', {name: '旅行 Café — a long trip name', destinationName: 'A long destination destination',
    startDate: '2027-12-30', endDate: '2028-01-03', inProgress: true, plannedCount: 2, bookedCount: 1,
    primaryBookingReference: 'REF-123', alternatives: [{id: 'option', lifecycle: 'PLANNED', version: 0, status: 'PLANNED', expired: false, name: 'Hidden detailed option'}]});
  const view = render(<TripListSection {...props} upcoming={[trip, summary('future')]} past={[]} />);
  const card = screen.getByRole('article', {name: trip.name});
  expect(within(card).getByRole('heading', {level: 3})).toBeInTheDocument();
  const elements = [within(card).getByRole('heading'), within(card).getByText(trip.destinationName),
    within(card).getByText('Dec 30, 2027 – Jan 3, 2028'), within(card).getByText('In progress'), within(card).getByRole('button', {name: /Open trip/})];
  for (let i = 1; i < elements.length; i++) expect(elements[i - 1].compareDocumentPosition(elements[i]) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  expect(within(card).getByText('2 Saved options')).toBeInTheDocument();
  expect(within(card).getByText('1 Booking')).toBeInTheDocument();
  expect(within(card).getByText('REF-123')).toBeInTheDocument();
  expect(screen.queryByText('Hidden detailed option')).not.toBeInTheDocument();
  expect(within(screen.getByRole('article', {name: 'future'})).queryByText('In progress')).not.toBeInTheDocument();
  view.rerender(<TripListSection {...props} headingLevel={3} upcoming={[summary('fallback', {name: undefined, startDate: '2027-03-30', endDate: '2027-04-02'})]} past={[]} />);
  expect(screen.getByRole('heading', {name: 'fallback', level: 4})).toBeInTheDocument();
  expect(screen.getByText('Mar 30, 2027 – Apr 2, 2027')).toBeInTheDocument();
  vi.useRealTimers();
});

it('names the menu and supports first/last focus, arrow navigation, Home/End and Escape', async () => {
  const user = userEvent.setup();
  render(<TripListSection {...props} upcoming={[summary('Trip')]} past={[]} onRenameTrip={vi.fn()} />);
  const trigger = screen.getByRole('button', {name: 'Actions for Trip'});
  expect(trigger).toHaveAttribute('aria-haspopup', 'menu');
  expect(trigger).toHaveAttribute('aria-expanded', 'false');
  trigger.focus(); await user.keyboard('{ArrowDown}');
  const menu = screen.getByRole('menu', {name: 'Actions for Trip'});
  expect(trigger.getAttribute('aria-controls')).toBe(menu.id);
  expect(trigger).toHaveAttribute('aria-expanded', 'true');
  const rename = within(menu).getByRole('menuitem', {name: 'Rename trip'});
  const remove = within(menu).getByRole('menuitem', {name: 'Delete trip Trip'});
  expect(rename).toHaveFocus();
  await user.keyboard('{ArrowDown}'); expect(remove).toHaveFocus();
  await user.keyboard('{ArrowDown}'); expect(rename).toHaveFocus();
  await user.keyboard('{End}'); expect(remove).toHaveFocus();
  await user.keyboard('{Home}'); expect(rename).toHaveFocus();
  await user.keyboard('{Escape}'); expect(trigger).toHaveFocus(); expect(screen.queryByRole('menu')).not.toBeInTheDocument();
  await user.keyboard('{ArrowUp}'); expect(screen.getByRole('menuitem', {name: 'Delete trip Trip'})).toHaveFocus();
  await user.keyboard('{Escape}{Enter}'); expect(screen.getByRole('menuitem', {name: 'Rename trip'})).toHaveFocus();
  await user.keyboard('{Escape} '); expect(screen.getByRole('menuitem', {name: 'Rename trip'})).toHaveFocus();
});

it('lets Tab and Shift+Tab leave, dismisses outside without stealing focus and closes another card menu', async () => {
  const user = userEvent.setup();
  const view = render(<TripListSection {...props} upcoming={[summary('Alpha'), summary('Beta')]} past={[]} onRenameTrip={vi.fn()} />);
  const alpha = screen.getByRole('button', {name: 'Actions for Alpha'});
  const beta = screen.getByRole('button', {name: 'Actions for Beta'});
  await user.click(alpha); await user.tab();
  expect(screen.queryByRole('menu')).not.toBeInTheDocument();
  expect(screen.getByRole('button', {name: 'Open trip Beta'})).toHaveFocus();
  await user.click(alpha); await user.tab({shift: true});
  expect(alpha).toHaveFocus();
  // Returning to the trigger is still within the menu's ownership, but Tab must dismiss it.
  expect(screen.queryByRole('menu')).not.toBeInTheDocument();
  await user.click(alpha); await user.click(beta);
  expect(screen.getAllByRole('menu')).toHaveLength(1);
  expect(screen.getByRole('menu', {name: 'Actions for Beta'})).toBeInTheDocument();
  await user.click(screen.getByRole('button', {name: 'Upcoming (2)'}));
  expect(screen.queryByRole('menu')).not.toBeInTheDocument();
  expect(screen.getByRole('button', {name: 'Upcoming (2)'})).toHaveFocus();
  await user.click(beta); view.unmount();
  expect(screen.queryByRole('menu')).not.toBeInTheDocument();
});

it.each([
  {status: 'ACTIVE', hasBookingHistory: false, temporalStatus: 'UPCOMING', expiredAlternativeCount: 0, action: 'Delete trip Trip', disabled: false},
  {status: 'ACTIVE', hasBookingHistory: true, temporalStatus: 'UPCOMING', expiredAlternativeCount: 0, action: 'Cancel trip Trip', disabled: false},
  {status: 'ACTIVE', hasBookingHistory: true, temporalStatus: 'PAST', expiredAlternativeCount: 0, action: 'Cancel trip Trip', disabled: true},
  {status: 'ACTIVE', hasBookingHistory: true, temporalStatus: 'UPCOMING', expiredAlternativeCount: 1, action: 'Cancel trip Trip', disabled: true},
  {status: 'CANCELED', hasBookingHistory: false, temporalStatus: 'UPCOMING', expiredAlternativeCount: 0, action: 'Trip Trip is canceled', disabled: true},
])('preserves secondary availability for $action ($status/$temporalStatus/$expiredAlternativeCount)', async fixture => {
  const user = userEvent.setup(); const onDelete = vi.fn(); const onCancel = vi.fn();
  const trip = summary('Trip', {...fixture, version: 7});
  render(<TripListSection upcoming={fixture.temporalStatus === 'PAST' ? [] : [trip]} past={fixture.temporalStatus === 'PAST' ? [trip] : []}
    onSelectTrip={props.onSelectTrip} onDeleteTrip={onDelete} onCancelTrip={onCancel} />);
  const trigger = screen.getByRole('button', {name: 'Actions for Trip'});
  await user.click(trigger);
  const item = screen.getByRole('menuitem', {name: fixture.action});
  expect(item).toHaveProperty('disabled', fixture.disabled);
  if (fixture.disabled) {
    await user.click(item); expect(onDelete).not.toHaveBeenCalled(); expect(onCancel).not.toHaveBeenCalled();
    await user.keyboard('{Escape}'); expect(trigger).toHaveFocus();
  } else {
    await user.click(item); expect(fixture.hasBookingHistory ? onCancel : onDelete).toHaveBeenCalledWith(trip);
    expect(trigger).toHaveFocus(); expect(screen.queryByRole('menu')).not.toBeInTheDocument();
  }
});

it('skips disabled items while preserving rename on cancelled and expired cards', async () => {
  const user = userEvent.setup();
  render(<TripListSection {...props} upcoming={[summary('Trip', {status: 'CANCELED'})]} past={[]} onRenameTrip={vi.fn()} />);
  await user.click(screen.getByRole('button', {name: 'Actions for Trip'}));
  await user.keyboard('{End}{ArrowDown}{ArrowUp}');
  expect(screen.getByRole('menuitem', {name: 'Rename trip'})).toHaveFocus();
  await user.click(screen.getByRole('menuitem', {name: 'Rename trip'}));
  expect(screen.getByLabelText('Trip name')).toHaveFocus();
  await user.keyboard('{Escape}'); expect(screen.getByLabelText('Trip name')).toBeInTheDocument();
  await user.click(screen.getByRole('button', {name: 'Cancel rename'}));
  expect(screen.getByRole('button', {name: 'Actions for Trip'})).toHaveFocus();
});

it('validates and trims rename, prevents pending submission and restores trigger after success', async () => {
  const user = userEvent.setup();
  let resolve!: () => void;
  const onRename = vi.fn(() => new Promise<void>(done => {resolve = done;}));
  const trip = summary('Trip', {version: 5});
  render(<TripListSection {...props} upcoming={[trip]} past={[]} onRenameTrip={onRename} />);
  await user.click(screen.getByRole('button', {name: 'Actions for Trip'}));
  await user.click(screen.getByRole('menuitem', {name: 'Rename trip'}));
  const input = screen.getByLabelText('Trip name');
  await user.clear(input); await user.click(screen.getByRole('button', {name: 'Save name'}));
  expect(screen.getByRole('alert')).toHaveTextContent('Enter a trip name.');
  expect(input).toHaveAttribute('aria-invalid', 'true');
  expect(input.getAttribute('aria-describedby')).toBe(screen.getByRole('alert').id);
  fireEvent.change(input, {target: {value: 'a'.repeat(301)}}); await user.click(screen.getByRole('button', {name: 'Save name'}));
  expect(screen.getByRole('alert')).toHaveTextContent('300 characters'); expect(onRename).not.toHaveBeenCalled();
  fireEvent.change(input, {target: {value: '  New name  '}}); await user.click(screen.getByRole('button', {name: 'Save name'}));
  expect(onRename).toHaveBeenCalledWith(trip, 'New name');
  expect(input).toBeDisabled(); expect(screen.getByRole('button', {name: 'Cancel rename'})).toBeDisabled();
  fireEvent.submit(input.closest('form')!); expect(onRename).toHaveBeenCalledTimes(1);
  await act(async () => resolve());
  expect(screen.queryByLabelText('Trip name')).not.toBeInTheDocument();
  expect(screen.getByRole('button', {name: 'Actions for Trip'})).toHaveFocus();
});

it.each(['VERSION_CONFLICT', 'TRIP_CANCELED'])('retains rename form, accessible error and callback restriction for %s', async code => {
  const user = userEvent.setup();
  const onRename = vi.fn().mockRejectedValue(new IdentityApiError('api', 409, code, {}, 'Trip cannot be changed.'));
  render(<TripListSection {...props} upcoming={[summary('Trip', {status: code === 'TRIP_CANCELED' ? 'CANCELED' : 'ACTIVE'})]} past={[]} onRenameTrip={onRename} />);
  await user.click(screen.getByRole('button', {name: 'Actions for Trip'}));
  await user.click(screen.getByRole('menuitem', {name: 'Rename trip'}));
  await user.click(screen.getByRole('button', {name: 'Save name'}));
  expect(await screen.findByRole('alert')).toHaveTextContent(code === 'VERSION_CONFLICT' ? 'changed on the server' : 'Trip cannot be changed.');
  expect(screen.getByLabelText('Trip name')).toHaveAttribute('aria-invalid', 'true');
  expect(screen.getByRole('button', {name: 'Save name'})).not.toBeDisabled();
});


it.each(['UPCOMING', 'PAST', 'CANCELED'])('uses name then ID for equal relevant dates in %s', state => {
  const trips = [summary('z', {name: 'Alpha'}), summary('b', {name: 'Zulu'}), summary('a', {name: 'Alpha'})].map(trip => ({...trip,
    status: state === 'CANCELED' ? 'CANCELED' : 'ACTIVE', temporalStatus: state === 'PAST' ? 'PAST' : 'UPCOMING'}));
  render(<TripListSection {...props} upcoming={state === 'PAST' ? [] : trips} past={state === 'PAST' ? trips : []} />);
  expect(screen.getAllByRole('article').map(card => card.getAttribute('aria-labelledby'))).toEqual(['trip-heading-a', 'trip-heading-z', 'trip-heading-b']);
});

it('keeps Open directly available on a cancelled card', async () => {
  const user = userEvent.setup(); const onSelect = vi.fn();
  render(<TripListSection {...props} onSelectTrip={onSelect} upcoming={[summary('Trip', {status: 'CANCELED'})]} past={[]} />);
  await user.click(screen.getByRole('button', {name: 'Open trip Trip'}));
  expect(onSelect).toHaveBeenCalledWith('Trip');
});
