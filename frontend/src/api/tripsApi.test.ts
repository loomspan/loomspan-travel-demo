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

  it('airfare search, selection, and removal send correct queries, payloads, and CSRF', async () => {
    fetchMock.mockResolvedValueOnce(json(200, {options: []}));
    await tripsApi.searchAirfare('trip-1', 'draft-1', {directOnly: true, sort: 'LOWEST_PRICE'});
    expect(fetchMock).toHaveBeenLastCalledWith(
      '/api/trips/trip-1/drafts/draft-1/airfare?directOnly=true&sort=LOWEST_PRICE',
      {
        method: 'GET',
        credentials: 'same-origin',
        headers: undefined,
        body: undefined,
      }
    );

    fetchMock.mockResolvedValueOnce(json(200, {id: 'trip-1', version: 6}));
    await tripsApi.selectAirfare('trip-1', 'draft-1', {
      expectedVersion: 5,
      expectedDraftVersion: 1,
      outboundFlightInstanceId: 101,
      returnFlightInstanceId: 202,
    });
    expect(fetchMock).toHaveBeenLastCalledWith('/api/trips/trip-1/drafts/draft-1/airfare', {
      method: 'PUT',
      credentials: 'same-origin',
      headers: {'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'secret-token'},
      body: JSON.stringify({
        expectedVersion: 5,
        expectedDraftVersion: 1,
        outboundFlightInstanceId: 101,
        returnFlightInstanceId: 202,
      }),
    });

    fetchMock.mockResolvedValueOnce(json(200, {id: 'trip-1', version: 7}));
    await tripsApi.removeAirfare('trip-1', 'draft-1', {
      expectedVersion: 6,
      expectedDraftVersion: 2,
    });
    expect(fetchMock).toHaveBeenLastCalledWith('/api/trips/trip-1/drafts/draft-1/airfare', {
      method: 'DELETE',
      credentials: 'same-origin',
      headers: {'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'secret-token'},
      body: JSON.stringify({
        expectedVersion: 6,
        expectedDraftVersion: 2,
      }),
    });
  });

  it('stay search, selection, and removal send correct queries, payloads, and CSRF', async () => {
    fetchMock.mockResolvedValueOnce(json(200, {options: []}));
    await tripsApi.searchStays('trip-1', 'draft-1', {type: 'HOTEL', sort: 'NEAREST_CITY_CENTER'});
    expect(fetchMock).toHaveBeenLastCalledWith(
      '/api/trips/trip-1/drafts/draft-1/stays?type=HOTEL&sort=NEAREST_CITY_CENTER',
      {
        method: 'GET',
        credentials: 'same-origin',
        headers: undefined,
        body: undefined,
      }
    );

    fetchMock.mockResolvedValueOnce(json(200, {id: 'trip-1', version: 8}));
    await tripsApi.selectStay('trip-1', 'draft-1', {
      expectedVersion: 7,
      expectedDraftVersion: 3,
      accommodationUnitId: 303,
      unitCount: 2,
    });
    expect(fetchMock).toHaveBeenLastCalledWith('/api/trips/trip-1/drafts/draft-1/stays', {
      method: 'PUT',
      credentials: 'same-origin',
      headers: {'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'secret-token'},
      body: JSON.stringify({
        expectedVersion: 7,
        expectedDraftVersion: 3,
        accommodationUnitId: 303,
        unitCount: 2,
      }),
    });

    fetchMock.mockResolvedValueOnce(json(200, {id: 'trip-1', version: 9}));
    await tripsApi.removeStay('trip-1', 'draft-1', {
      expectedVersion: 8,
      expectedDraftVersion: 4,
    });
    expect(fetchMock).toHaveBeenLastCalledWith('/api/trips/trip-1/drafts/draft-1/stays', {
      method: 'DELETE',
      credentials: 'same-origin',
      headers: {'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'secret-token'},
      body: JSON.stringify({
        expectedVersion: 8,
        expectedDraftVersion: 4,
      }),
    });
  });

  it('rental search, selection, and removal send correct queries, payloads, and CSRF', async () => {
    fetchMock.mockResolvedValueOnce(json(200, {options: []}));
    await tripsApi.searchRentals('trip-1', 'draft-1', {
      pickupAt: '2027-03-10T10:00:00Z',
      returnAt: '2027-03-14T10:00:00Z',
      sort: 'LOWEST_PRICE',
    });
    expect(fetchMock).toHaveBeenLastCalledWith(
      '/api/trips/trip-1/drafts/draft-1/rentals?pickupAt=2027-03-10T10%3A00%3A00Z&returnAt=2027-03-14T10%3A00%3A00Z&sort=LOWEST_PRICE',
      {
        method: 'GET',
        credentials: 'same-origin',
        headers: undefined,
        body: undefined,
      }
    );

    fetchMock.mockResolvedValueOnce(json(200, {id: 'trip-1', version: 10}));
    await tripsApi.selectRental('trip-1', 'draft-1', {
      expectedVersion: 9,
      expectedDraftVersion: 5,
      rentalUnitId: 404,
      pickupAt: '2027-03-10T10:00:00Z',
      returnAt: '2027-03-14T10:00:00Z',
    });
    expect(fetchMock).toHaveBeenLastCalledWith('/api/trips/trip-1/drafts/draft-1/rentals', {
      method: 'PUT',
      credentials: 'same-origin',
      headers: {'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'secret-token'},
      body: JSON.stringify({
        expectedVersion: 9,
        expectedDraftVersion: 5,
        rentalUnitId: 404,
        pickupAt: '2027-03-10T10:00:00Z',
        returnAt: '2027-03-14T10:00:00Z',
      }),
    });

    fetchMock.mockResolvedValueOnce(json(200, {id: 'trip-1', version: 11}));
    await tripsApi.removeRental('trip-1', 'draft-1', {
      expectedVersion: 10,
      expectedDraftVersion: 6,
    });
    expect(fetchMock).toHaveBeenLastCalledWith('/api/trips/trip-1/drafts/draft-1/rentals', {
      method: 'DELETE',
      credentials: 'same-origin',
      headers: {'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'secret-token'},
      body: JSON.stringify({
        expectedVersion: 10,
        expectedDraftVersion: 6,
      }),
    });
  });

  it('getDraftReadiness sends GET request and returns readiness response', async () => {
    fetchMock.mockResolvedValueOnce(json(200, {
      ready: true,
      blockingIssues: {},
      isOverBudget: false,
      budgetOverageCents: 0,
      requiresOverageAcknowledgment: false,
    }));

    const result = await tripsApi.getDraftReadiness('trip-1', 'draft-1');
    expect(result.ready).toBe(true);
    expect(fetchMock).toHaveBeenCalledWith('/api/trips/trip-1/drafts/draft-1/readiness', {
      method: 'GET',
      credentials: 'same-origin',
      headers: undefined,
      body: undefined,
    });
  });

  it('promoteDraft sends POST request with expected versions and overage acknowledgment', async () => {
    fetchMock.mockResolvedValueOnce(json(201, {id: 'trip-1', version: 2}));

    const result = await tripsApi.promoteDraft('trip-1', 'draft-1', {
      expectedVersion: 1,
      expectedDraftVersion: 0,
      budgetOverageAcknowledged: true,
    });

    expect(result).toEqual({id: 'trip-1', version: 2});
    expect(fetchMock).toHaveBeenCalledWith('/api/trips/trip-1/drafts/draft-1/plan', {
      method: 'POST',
      credentials: 'same-origin',
      headers: {'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'secret-token'},
      body: JSON.stringify({
        expectedVersion: 1,
        expectedDraftVersion: 0,
        budgetOverageAcknowledged: true,
      }),
    });
  });

  it('createBooking sends POST request with idempotency key in header and body', async () => {
    fetchMock.mockResolvedValueOnce(json(201, {id: 'booking-1', bookingReference: 'DT-ABC123', status: 'ACTIVE'}));

    const result = await tripsApi.createBooking('trip-1', {
      plannedItineraryId: 'planned-1',
      expectedVersion: 1,
      idempotencyKey: 'book-uuid-123',
    });

    expect(result.id).toBe('booking-1');
    expect(result.bookingReference).toBe('DT-ABC123');
    expect(fetchMock).toHaveBeenCalledWith('/api/trips/trip-1/bookings', {
      method: 'POST',
      credentials: 'same-origin',
      headers: {
        'Content-Type': 'application/json',
        'X-XSRF-TOKEN': 'secret-token',
        'Idempotency-Key': 'book-uuid-123',
      },
      body: JSON.stringify({
        plannedItineraryId: 'planned-1',
        expectedVersion: 1,
        idempotencyKey: 'book-uuid-123',
      }),
    });
  });

  it('getActiveBooking sends GET request to active booking endpoint', async () => {
    fetchMock.mockResolvedValueOnce(json(200, {id: 'booking-1', status: 'ACTIVE'}));

    const result = await tripsApi.getActiveBooking('trip-1');
    expect(result.status).toBe('ACTIVE');
    expect(fetchMock).toHaveBeenCalledWith('/api/trips/trip-1/bookings/active', {
      method: 'GET',
      credentials: 'same-origin',
      headers: undefined,
      body: undefined,
    });
  });

  it('getBookingHistory sends GET request to bookings history endpoint', async () => {
    fetchMock.mockResolvedValueOnce(json(200, [{id: 'booking-1', status: 'ACTIVE'}]));

    const result = await tripsApi.getBookingHistory('trip-1');
    expect(result).toHaveLength(1);
    expect(fetchMock).toHaveBeenCalledWith('/api/trips/trip-1/bookings', {
      method: 'GET',
      credentials: 'same-origin',
      headers: undefined,
      body: undefined,
    });
  });

  it('cancelBooking and cancelTrip API client methods send expectedVersion and CSRF headers', async () => {
    fetchMock.mockResolvedValueOnce(json(200, {id: 'trip-1', version: 2, status: 'ACTIVE'}));

    const bookingResult = await tripsApi.cancelBooking('trip-1', 'booking-1', {expectedVersion: 1});
    expect(bookingResult.version).toBe(2);
    expect(fetchMock).toHaveBeenCalledWith('/api/trips/trip-1/bookings/booking-1/cancel', {
      method: 'POST',
      credentials: 'same-origin',
      headers: {
        'Content-Type': 'application/json',
        'X-XSRF-TOKEN': 'secret-token',
      },
      body: JSON.stringify({expectedVersion: 1}),
    });

    fetchMock.mockResolvedValueOnce(json(200, {id: 'trip-1', version: 3, status: 'CANCELED'}));

    const tripResult = await tripsApi.cancelTrip('trip-1', {expectedVersion: 2});
    expect(tripResult.status).toBe('CANCELED');
    expect(fetchMock).toHaveBeenCalledWith('/api/trips/trip-1/cancel', {
      method: 'POST',
      credentials: 'same-origin',
      headers: {
        'Content-Type': 'application/json',
        'X-XSRF-TOKEN': 'secret-token',
      },
      body: JSON.stringify({expectedVersion: 2}),
    });
  });
});
