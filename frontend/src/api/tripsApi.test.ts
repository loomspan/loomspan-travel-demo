import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {tripsApi} from './tripsApi';

describe('tripsApi client', () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock);
    document.cookie = 'XSRF-TOKEN=secret-token; path=/';
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    document.cookie = 'XSRF-TOKEN=; max-age=0; path=/';
    fetchMock.mockReset();
  });

  const json = (status: number, body: unknown) =>
    new Response(JSON.stringify(body), {status, headers: {'Content-Type': 'application/json'}});
  const noContent = () => new Response(null, {status: 204});

  it('createTrip sends strictly structured payload and CSRF header', async () => {
    fetchMock.mockResolvedValueOnce(json(201, {id: 'trip-1'}));

    const result = await tripsApi.createTrip({
      destinationKey: 'destination-sfo',
      startDate: '2027-03-10',
      endDate: '2027-03-14',
      travelerCount: 2,
    });

    expect(result).toEqual({id: 'trip-1'});
    expect(fetchMock).toHaveBeenCalledWith('/api/trips', {
      method: 'POST',
      credentials: 'same-origin',
      headers: {
        'Content-Type': 'application/json',
        'X-XSRF-TOKEN': 'secret-token',
      },
      body: JSON.stringify({
        destinationKey: 'destination-sfo',
        startDate: '2027-03-10',
        endDate: '2027-03-14',
        travelerCount: 2,
      }),
    });
  });

  it('replaceSharedDetails sends expectedVersion and allowed update fields', async () => {
    fetchMock.mockResolvedValueOnce(json(200, {id: 'trip-1', version: 1}));

    await tripsApi.replaceSharedDetails('trip-1', {
      expectedVersion: 0,
      destinationKey: 'destination-muc',
      startDate: '2027-03-15',
      endDate: '2027-03-20',
      travelerCount: 3,
      travelerAges: [30, 28, 5],
      budgetCents: 500000,
    });

    expect(fetchMock).toHaveBeenCalledWith('/api/trips/trip-1', {
      method: 'PUT',
      credentials: 'same-origin',
      headers: {
        'Content-Type': 'application/json',
        'X-XSRF-TOKEN': 'secret-token',
      },
      body: JSON.stringify({
        expectedVersion: 0,
        destinationKey: 'destination-muc',
        startDate: '2027-03-15',
        endDate: '2027-03-20',
        travelerCount: 3,
        travelerAges: [30, 28, 5],
        budgetCents: 500000,
      }),
    });
  });

  it('duplicateTrip sends revision payload with sourcePlannedItineraryIds', async () => {
    fetchMock.mockResolvedValueOnce(json(201, {id: 'trip-new', version: 0}));

    await tripsApi.duplicateTrip('trip-1', {
      expectedVersion: 2,
      destinationKey: 'destination-mex',
      startDate: '2027-03-12',
      endDate: '2027-03-18',
      travelerCount: 1,
      sourcePlannedItineraryIds: ['alt-1', 'alt-2'],
    });

    expect(fetchMock).toHaveBeenCalledWith('/api/trips/trip-1/duplicate', {
      method: 'POST',
      credentials: 'same-origin',
      headers: {
        'Content-Type': 'application/json',
        'X-XSRF-TOKEN': 'secret-token',
      },
      body: JSON.stringify({
        expectedVersion: 2,
        destinationKey: 'destination-mex',
        startDate: '2027-03-12',
        endDate: '2027-03-18',
        travelerCount: 1,
        sourcePlannedItineraryIds: ['alt-1', 'alt-2'],
      }),
    });
  });

  it('draft operations send correct endpoints and expected versions', async () => {
    fetchMock.mockResolvedValueOnce(json(201, {id: 'trip-1', version: 1}));
    await tripsApi.createDraft('trip-1', {expectedVersion: 0});
    expect(fetchMock).toHaveBeenLastCalledWith('/api/trips/trip-1/drafts', {
      method: 'POST',
      credentials: 'same-origin',
      headers: {'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'secret-token'},
      body: JSON.stringify({expectedVersion: 0}),
    });

    fetchMock.mockResolvedValueOnce(json(201, {id: 'trip-1', version: 2}));
    await tripsApi.duplicateDraft('trip-1', 'draft-1', {expectedVersion: 1, expectedDraftVersion: 0});
    expect(fetchMock).toHaveBeenLastCalledWith('/api/trips/trip-1/drafts/draft-1/duplicate', {
      method: 'POST',
      credentials: 'same-origin',
      headers: {'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'secret-token'},
      body: JSON.stringify({expectedVersion: 1, expectedDraftVersion: 0}),
    });

    fetchMock.mockResolvedValueOnce(json(201, {id: 'trip-1', version: 3}));
    await tripsApi.duplicateAlternative('trip-1', 'alt-planned', {expectedVersion: 2});
    expect(fetchMock).toHaveBeenLastCalledWith('/api/trips/trip-1/alternatives/alt-planned/duplicate', {
      method: 'POST',
      credentials: 'same-origin',
      headers: {'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'secret-token'},
      body: JSON.stringify({expectedVersion: 2}),
    });
  });

  it('deletion operations send expected bodies with DELETE method', async () => {
    fetchMock.mockResolvedValueOnce(json(200, {id: 'trip-1', version: 4}));
    await tripsApi.deleteDraft('trip-1', 'draft-1', {expectedVersion: 3, expectedDraftVersion: 1});
    expect(fetchMock).toHaveBeenLastCalledWith('/api/trips/trip-1/drafts/draft-1', {
      method: 'DELETE',
      credentials: 'same-origin',
      headers: {'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'secret-token'},
      body: JSON.stringify({expectedVersion: 3, expectedDraftVersion: 1}),
    });

    fetchMock.mockResolvedValueOnce(json(200, {id: 'trip-1', version: 5}));
    await tripsApi.deleteAlternative('trip-1', 'alt-1', {expectedVersion: 4, confirmed: true});
    expect(fetchMock).toHaveBeenLastCalledWith('/api/trips/trip-1/alternatives/alt-1', {
      method: 'DELETE',
      credentials: 'same-origin',
      headers: {'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'secret-token'},
      body: JSON.stringify({expectedVersion: 4, confirmed: true}),
    });

    fetchMock.mockResolvedValueOnce(noContent());
    await tripsApi.deleteTrip('trip-1', {
      expectedVersion: 5,
      expectedDraftCount: 2,
      expectedPlannedCount: 1,
      confirmed: true,
    });
    expect(fetchMock).toHaveBeenLastCalledWith('/api/trips/trip-1', {
      method: 'DELETE',
      credentials: 'same-origin',
      headers: {'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'secret-token'},
      body: JSON.stringify({
        expectedVersion: 5,
        expectedDraftCount: 2,
        expectedPlannedCount: 1,
        confirmed: true,
      }),
    });
  });
});
