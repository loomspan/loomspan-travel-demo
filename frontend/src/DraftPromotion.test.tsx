import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {render, screen, waitFor, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {TripWorkspace} from './components/TripWorkspace';
import {tripsApi, type TripResponse} from './api/tripsApi';
import {IdentityApiError} from './api/identityApi';

const empty = {airfare: null, stay: null, rental: null};
const withAirfare = {airfare: {outboundDescription: 'Morning flight', returnDescription: 'Evening return'} as TripResponse['drafts'][number]['selections']['airfare'], stay: null, rental: null};

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
    workspace(trip({drafts: [{id: 'working-1', version: 0, startDate: '2027-03-10', endDate: '2027-03-14', selections: withAirfare}]}));
    expect(save).not.toHaveBeenCalled();
    await user.type(screen.getByLabelText('Option name'), 'First choice');
    expect(save).not.toHaveBeenCalled();
    await user.click(screen.getByRole('button', {name: 'Save as new option'}));
    await waitFor(() => expect(save).toHaveBeenCalledWith('trip-1', {
      expectedVersion: 0, expectedDraftVersion: 0, name: 'First choice',
    }));
    expect(await screen.findByText('Saved as a new option.')).toBeInTheDocument();
  });

  it('shows one Working plan and keeps empty work editable without offering an empty option', () => {
    workspace(withOption());
    expect(screen.getAllByRole('heading', {name: 'Working plan'})).toHaveLength(1);
    expect(screen.getByRole('heading', {name: 'Saved options (1)'})).toBeInTheDocument();
    expect(screen.getByRole('heading', {name: 'First choice'})).toBeInTheDocument();
    expect(screen.getByText('2027-03-15 to 2027-03-19')).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Save as new option'})).toBeDisabled();
    expect(screen.getByText(/keep editing this incomplete Working plan/i)).toBeInTheDocument();
    expect(screen.queryByText(/Draft v\d|Planned itinerary|Create empty draft|Duplicate draft/)).not.toBeInTheDocument();
  });

  it('renames an unbooked option without using the snapshot update action', async () => {
    const renamed = withOption();
    renamed.savedOptions = renamed.savedOptions?.map(option => ({...option, name: 'Later choice', version: 1}));
    renamed.planned = renamed.savedOptions ?? [];
    const rename = vi.spyOn(tripsApi, 'renameOption').mockResolvedValue({...renamed, version: 2});
    const update = vi.spyOn(tripsApi, 'updateOption');
    const user = userEvent.setup();
    workspace(withOption());
    await user.click(screen.getByRole('button', {name: 'Rename First choice'}));
    const dialog = screen.getByRole('dialog', {name: 'Rename Saved option'});
    await user.clear(within(dialog).getByLabelText('Option name'));
    await user.type(within(dialog).getByLabelText('Option name'), 'Later choice');
    await user.click(within(dialog).getByRole('button', {name: 'Save name'}));
    await waitFor(() => expect(rename).toHaveBeenCalledWith('trip-1', 'option-1', {
      expectedVersion: 1, expectedOptionVersion: 0, name: 'Later choice',
    }));
    expect(update).not.toHaveBeenCalled();
    expect(await screen.findByRole('heading', {name: 'Later choice'})).toBeInTheDocument();
  });

  it('keeps unsaved Working edits when rename is attempted and restores dialog focus', async () => {
    const rename = vi.spyOn(tripsApi, 'renameOption');
    const user = userEvent.setup();
    workspace(withOption());
    const renameButton = screen.getByRole('button', {name: 'Rename First choice'});
    await user.click(renameButton);
    const dialog = screen.getByRole('dialog', {name: 'Rename Saved option'});
    expect(within(dialog).getByLabelText('Option name')).toHaveFocus();
    await user.keyboard('{Escape}');
    expect(dialog).not.toBeInTheDocument();
    expect(renameButton).toHaveFocus();

    await user.clear(screen.getByLabelText('Budget (USD)'));
    await user.type(screen.getByLabelText('Budget (USD)'), '500');
    await user.click(renameButton);
    await user.click(within(screen.getByRole('dialog')).getByRole('button', {name: 'Save name'}));
    expect(rename).not.toHaveBeenCalled();
    expect(screen.getByLabelText('Budget (USD)')).toHaveValue(500);
    expect(screen.getByText(/Save your Working plan before managing options/)).toBeInTheDocument();
  });

  it('keeps a failed save visible and reports session expiry without claiming success', async () => {
    const initial = trip({drafts: [{id: 'working-1', version: 0, startDate: '2027-03-10',
      endDate: '2027-03-14', selections: withAirfare}]});
    const onAuthenticationRequired = vi.fn();
    vi.spyOn(tripsApi, 'saveOption').mockRejectedValue(
      new IdentityApiError('api', 401, 'UNAUTHENTICATED', {}, 'Session expired'));
    const user = userEvent.setup();
    render(<TripWorkspace initialTrip={initial} onBack={() => {}} onTripDeleted={() => {}}
      onAuthenticationRequired={onAuthenticationRequired} />);
    await user.type(screen.getByLabelText('Option name'), 'Later choice');
    await user.click(screen.getByRole('button', {name: 'Save as new option'}));
    expect(await screen.findByText('Session expired')).toBeInTheDocument();
    expect(onAuthenticationRequired).toHaveBeenCalledOnce();
    expect(screen.queryByText('Saved as a new option.')).not.toBeInTheDocument();
    expect(screen.getByRole('heading', {name: 'Saved options (0)'})).toBeInTheDocument();
  });

  it('does not hide a failed new-option save after an unrelated rename succeeds', async () => {
    const initial = withOption();
    initial.drafts = [{...initial.drafts[0], selections: withAirfare}];
    vi.spyOn(tripsApi, 'saveOption').mockRejectedValue(new Error('Network unavailable'));
    vi.spyOn(tripsApi, 'renameOption').mockResolvedValue({...initial, version: 2});
    const user = userEvent.setup();
    workspace(initial);
    await user.type(screen.getByLabelText('Option name'), 'Another choice');
    await user.click(screen.getByRole('button', {name: 'Save as new option'}));
    expect(await screen.findByText(/Save as new option failed: Could not save option/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: 'Rename First choice'}));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', {name: 'Save name'}));
    await screen.findByText('Option renamed.');
    expect(screen.getByText(/Save as new option failed: Could not save option/)).toBeInTheDocument();
  });

  it('retains a date-change summary across unrelated option actions until acknowledged', async () => {
    const initial = withOption();
    initial.revisionSummary = {removals: [{draftId: 'working-1', component: 'STAY', reason: 'Dates no longer available'}], adjustments: []};
    const renamed = withOption();
    vi.spyOn(tripsApi, 'renameOption').mockResolvedValue({...renamed, version: 2});
    const user = userEvent.setup();
    workspace(initial);
    expect(screen.getByRole('heading', {name: 'Review changes to your Working plan'})).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: 'Rename First choice'}));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', {name: 'Save name'}));
    await screen.findByText('Option renamed.');
    expect(screen.getByText(/Dates no longer available/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: 'Acknowledge date change summary'}));
    expect(screen.queryByRole('heading', {name: 'Review changes to your Working plan'})).not.toBeInTheDocument();
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
    await user.click(screen.getByRole('button', {name: /open first choice as a copy/i}));
    const dialog = screen.getByRole('dialog', {name: /open saved option for editing/i});
    await user.click(within(dialog).getByRole('button', {name: 'Cancel'}));
    expect(load).not.toHaveBeenCalled();
    await user.click(screen.getByRole('button', {name: /open first choice as a copy/i}));
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
    const original = withOption();
    original.drafts = [{...original.drafts[0], selections: withAirfare}];
    workspace(original);
    await user.click(screen.getByRole('button', {name: /open first choice as a copy/i}));
    const dialog = screen.getByRole('dialog', {name: /open saved option for editing/i});
    expect(within(dialog).getByRole('button', {name: /save current first/i})).toBeDisabled();
    await user.type(within(dialog).getByLabelText('Name the current Working plan to keep both'), 'Keep current');
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
    const loaded = trip({version: 2, drafts: [{id: 'working-1', version: 0, startDate: '2027-03-15', endDate: '2027-03-19', selections: withAirfare}],
      planned: withOption().planned, savedOptions: withOption().savedOptions,
      alternatives: withOption().alternatives});
    vi.spyOn(tripsApi, 'loadOption').mockResolvedValue(loaded);
    const update = vi.spyOn(tripsApi, 'updateOption').mockRejectedValue(
      new IdentityApiError('api', 409, 'VERSION_CONFLICT', {}, 'Option changed'));
    const user = userEvent.setup();
    workspace(withOption());
    await user.click(screen.getByRole('button', {name: /open first choice as a copy/i}));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', {name: 'Replace Working plan'}));
    await screen.findByRole('button', {name: 'Update this option'});
    await user.click(screen.getByRole('button', {name: 'Update this option'}));
    await user.click(within(screen.getByRole('dialog', {name: /replace first choice/i})).getByRole('button', {name: 'Replace Saved option'}));
    await waitFor(() => expect(update).toHaveBeenCalledWith('trip-1', 'option-1', {
      expectedVersion: 2, expectedDraftVersion: 0, expectedOptionVersion: 0, name: 'First choice',
    }));
    expect(await screen.findByRole('button', {name: 'Reload from server'})).toBeInTheDocument();
  });
});
