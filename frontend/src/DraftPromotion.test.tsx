import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {render, screen, waitFor, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {TripWorkspace} from './components/TripWorkspace';
import {tripsApi, type TripResponse} from './api/tripsApi';
import {IdentityApiError} from './api/identityApi';

const empty = {airfare: null, stay: null, rental: null};

function trip(overrides: Partial<TripResponse> = {}): TripResponse {
  return {
    id: 'trip-1', destinationKey: 'destination-sfo', destinationName: 'San Francisco',
    originAirportCode: 'PDX', startDate: '2027-03-10', endDate: '2027-03-14',
    travelerCount: 2, travelerAges: [30, 28], budgetCents: null,
    label: 'Spring trip', version: 0,
    drafts: [{id: 'working-1', version: 0, startDate: '2027-03-10', endDate: '2027-03-14', selections: empty}],
    planned: [], savedOptions: [],
    alternatives: [{id: 'working-1', lifecycle: 'DRAFT', version: 0, selections: empty}],
    revisionSummary: null,
    ...overrides,
  };
}

function withOption(): TripResponse {
  const option = {id: 'option-1', name: 'First choice', version: 0,
    startDate: '2027-03-15', endDate: '2027-03-19', selections: empty};
  return trip({version: 1, planned: [option], savedOptions: [option],
    alternatives: [
      {id: 'working-1', lifecycle: 'DRAFT', version: 0, selections: empty},
      {id: 'option-1', lifecycle: 'PLANNED', version: 0, name: 'First choice',
        startDate: '2027-03-15', endDate: '2027-03-19', selections: empty},
    ]});
}

function workspace(initialTrip: TripResponse) {
  return render(<TripWorkspace initialTrip={initialTrip} onBack={() => {}} onTripDeleted={() => {}} />);
}

describe('Saved option lifecycle', () => {
  beforeEach(() => { document.cookie = 'XSRF-TOKEN=secret-token; path=/'; });
  afterEach(() => { vi.restoreAllMocks(); document.cookie = 'XSRF-TOKEN=; max-age=0; path=/'; });

  it('does not create an option until named save is requested', async () => {
    const save = vi.spyOn(tripsApi, 'saveOption').mockResolvedValue(withOption());
    const user = userEvent.setup();
    workspace(trip());
    expect(save).not.toHaveBeenCalled();
    await user.type(screen.getByLabelText('Option name'), 'First choice');
    expect(save).not.toHaveBeenCalled();
    await user.click(screen.getByRole('button', {name: 'Save as new option'}));
    await waitFor(() => expect(save).toHaveBeenCalledWith('trip-1', {
      expectedVersion: 0, expectedDraftVersion: 0, name: 'First choice',
    }));
    expect(await screen.findByText('Saved as a new option.')).toBeInTheDocument();
  });

  it('canceling an option load makes no request and explicit replacement keeps its source', async () => {
    const load = vi.spyOn(tripsApi, 'loadOption').mockResolvedValue(trip({
      version: 2, drafts: [{id: 'working-1', version: 1, startDate: '2027-03-15',
        endDate: '2027-03-19', selections: empty}],
      planned: withOption().planned, savedOptions: withOption().savedOptions,
      alternatives: withOption().alternatives,
    }));
    const user = userEvent.setup();
    workspace(withOption());
    await user.click(screen.getByRole('button', {name: 'Open for editing'}));
    const dialog = screen.getByRole('dialog', {name: /open saved option for editing/i});
    await user.click(within(dialog).getByRole('button', {name: 'Cancel'}));
    expect(load).not.toHaveBeenCalled();
    await user.click(screen.getByRole('button', {name: 'Open for editing'}));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', {name: 'Replace Working plan'}));
    await waitFor(() => expect(load).toHaveBeenCalledWith('trip-1', 'option-1', {
      expectedVersion: 1, expectedDraftVersion: 0, expectedOptionVersion: 0, replaceWorking: true,
    }));
    expect(await screen.findByText(/saved option is unchanged/i)).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Update this option'})).toBeInTheDocument();
  });

  it('keeps the current Working plan as a separate named option before loading', async () => {
    const save = vi.spyOn(tripsApi, 'saveOption').mockResolvedValue(trip({
      version: 2, planned: withOption().planned, savedOptions: withOption().savedOptions,
      alternatives: withOption().alternatives,
    }));
    const load = vi.spyOn(tripsApi, 'loadOption')
      .mockRejectedValueOnce(new IdentityApiError('api', 503, 'SERVICE_UNAVAILABLE', {}, 'Try again'))
      .mockResolvedValue(trip({version: 3}));
    const user = userEvent.setup();
    workspace(withOption());
    await user.type(screen.getByLabelText('Option name'), 'Keep current');
    await user.click(screen.getByRole('button', {name: 'Open for editing'}));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', {name: /save current first/i}));
    await waitFor(() => expect(save).toHaveBeenCalledWith('trip-1', {
      expectedVersion: 1, expectedDraftVersion: 0, name: 'Keep current',
    }));
    expect(load).toHaveBeenCalledWith('trip-1', 'option-1', {
      expectedVersion: 2, expectedDraftVersion: 0, expectedOptionVersion: 0, replaceWorking: true,
    });
    await user.click(await screen.findByRole('button', {name: 'Retry opening option'}));
    await waitFor(() => expect(load).toHaveBeenCalledTimes(2));
    expect(save).toHaveBeenCalledTimes(1);
  });

  it('updates the selected option in place and presents stale conflicts', async () => {
    const loaded = trip({version: 2, planned: withOption().planned, savedOptions: withOption().savedOptions,
      alternatives: withOption().alternatives});
    vi.spyOn(tripsApi, 'loadOption').mockResolvedValue(loaded);
    const update = vi.spyOn(tripsApi, 'updateOption').mockRejectedValue(
      new IdentityApiError('api', 409, 'VERSION_CONFLICT', {}, 'Option changed'));
    const user = userEvent.setup();
    workspace(withOption());
    await user.click(screen.getByRole('button', {name: 'Open for editing'}));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', {name: 'Replace Working plan'}));
    await screen.findByRole('button', {name: 'Update this option'});
    await user.click(screen.getByRole('button', {name: 'Update this option'}));
    await waitFor(() => expect(update).toHaveBeenCalledWith('trip-1', 'option-1', {
      expectedVersion: 2, expectedDraftVersion: 0, expectedOptionVersion: 0, name: 'First choice',
    }));
    expect(await screen.findByRole('button', {name: 'Reload from server'})).toBeInTheDocument();
  });
});
