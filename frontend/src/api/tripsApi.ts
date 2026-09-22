import {request} from './identityApi';

export type AlternativeProfileSummary = {
  id: string;
  lifecycle: 'DRAFT' | 'PLANNED' | string;
  version: number | null;
  status: 'DRAFT' | 'PLANNED' | 'EXPIRED' | string;
  expired: boolean;
};

export type TripProfileSummary = {
  id: string;
  destinationKey: string;
  destinationName: string;
  startDate: string;
  endDate: string;
  label: string;
  version: number;
  temporalStatus: 'UPCOMING' | 'PAST' | string;
  draftCount: number;
  plannedCount: number;
  expiredAlternativeCount: number;
  bookedCount: number;
  hasBookingHistory: boolean;
  alternatives: AlternativeProfileSummary[];
};

export type TripsProfileResponse = {
  upcoming: TripProfileSummary[];
  past: TripProfileSummary[];
};

export type AirfareComponentResponse = {
  outboundFlightInstanceId: number;
  returnFlightInstanceId: number;
  outboundDescription: string;
  returnDescription: string;
  outboundBaseFareCents: number;
  outboundTaxCents: number;
  outboundFeeCents: number;
  returnBaseFareCents: number;
  returnTaxCents: number;
  returnFeeCents: number;
};

export type StayNightResponse = {
  date: string;
  basePriceCents: number;
  taxCents: number;
  feeCents: number;
};

export type StayComponentResponse = {
  accommodationUnitId: number;
  unitCount: number;
  propertyName: string;
  unitName: string;
  nights: StayNightResponse[];
};

export type RentalComponentResponse = {
  rentalUnitId: number;
  pickupAt: string;
  returnAt: string;
  locationName: string;
  vehicleClassName: string;
  unitIdentifier: string;
  dailyBasePriceCents: number;
  dailyTaxCents: number;
  dailyFeeCents: number;
};

export type AirfareSort =
  | 'DEFAULT'
  | 'LOWEST_PRICE'
  | 'SHORTEST_DURATION'
  | 'EARLIEST_DEPARTURE'
  | 'FEWEST_STOPS';

export type StaySort =
  | 'DEFAULT'
  | 'LOWEST_PRICE'
  | 'HIGHEST_RATING'
  | 'NEAREST_CITY_CENTER';

export type RentalSort =
  | 'DEFAULT'
  | 'LOWEST_PRICE';

export type AccommodationType =
  | 'HOTEL'
  | 'BED_AND_BREAKFAST'
  | 'VACATION_RENTAL';

export type LayoverResponse = {
  airportCode: string;
  airportName: string;
  durationMinutes: number;
};

export type FlightLegResponse = {
  flightInstanceId: number;
  catalogKey: string;
  carrier: string;
  flightNumber: string;
  stopCount: number;
  originAirportCode: string;
  originAirportName: string;
  destinationAirportCode: string;
  destinationAirportName: string;
  departureTime: string;
  arrivalTime: string;
  departureTimeZone: string;
  arrivalTimeZone: string;
  durationMinutes: number;
  availableSeats: number;
  baseFareCents: number;
  taxCents: number;
  feeCents: number;
  totalFareCents: number;
  layover?: LayoverResponse | null;
};

export type PartyPricingResponse = {
  travelerCount: number;
  perTravelerBaseFareCents: number;
  perTravelerTaxCents: number;
  perTravelerFeeCents: number;
  perTravelerTotalCents: number;
  partyBaseFareCents: number;
  partyTaxCents: number;
  partyFeeCents: number;
  partyTotalPriceCents: number;
};

export type FlightCombinationResponse = {
  combinationKey: string;
  outbound: FlightLegResponse;
  returnFlight: FlightLegResponse;
  totalDurationMinutes: number;
  direct: boolean;
  pricing: PartyPricingResponse;
};

export type AirfareSearchResponse = {
  tripId: string;
  draftId?: string;
  destinationKey: string;
  originAirportCode: string;
  destinationAirportCode: string;
  startDate: string;
  endDate: string;
  travelerCount: number;
  directOnly: boolean;
  sort: AirfareSort;
  options: FlightCombinationResponse[];
};

export type StayNightPricingResponse = {
  date: string;
  basePriceCents: number;
  taxCents: number;
  feeCents: number;
  totalCents: number;
  availableInventory: number;
};

export type StayPricingResponse = {
  requiredRooms: number;
  nightCount: number;
  perRoomBasePriceCents: number;
  perRoomTaxCents: number;
  perRoomFeeCents: number;
  perRoomTotalPriceCents: number;
  totalBasePriceCents: number;
  totalTaxCents: number;
  totalFeeCents: number;
  totalPriceCents: number;
  nights: StayNightPricingResponse[];
};

export type StayOptionResponse = {
  accommodationUnitId: number;
  propertyId: number;
  propertyCatalogKey: string;
  unitCatalogKey: string;
  propertyName: string;
  unitName: string;
  propertyCategory: string;
  unitKind: string;
  locationDescription: string;
  guestRating: number;
  distanceToCityCenterMeters: number;
  latitude: number;
  longitude: number;
  guestCapacity: number;
  inventoryCapacity: number;
  pricing: StayPricingResponse;
  fitsBudget?: boolean | null;
};

export type StaySearchResponse = {
  tripId: string;
  draftId?: string;
  destinationKey: string;
  accommodationType: AccommodationType;
  startDate: string;
  endDate: string;
  travelerCount: number;
  availableTripBudgetCents?: number | null;
  sort: StaySort;
  options: StayOptionResponse[];
};

export type RentalPricingResponse = {
  billingCycles: number;
  dailyBasePriceCents: number;
  dailyTaxCents: number;
  dailyFeeCents: number;
  dailyTotalPriceCents: number;
  totalBasePriceCents: number;
  totalTaxCents: number;
  totalFeeCents: number;
  totalPriceCents: number;
};

export type RentalOptionResponse = {
  rentalUnitId: number;
  unitCatalogKey: string;
  unitIdentifier: string;
  vehicleClassId: number;
  vehicleClassCatalogKey: string;
  vehicleClassName: string;
  vehicleCategory: string;
  locationId: number;
  locationCatalogKey: string;
  locationName: string;
  airportIataCode: string;
  pricing: RentalPricingResponse;
  fitsBudget?: boolean | null;
};

export type RentalSearchResponse = {
  tripId: string;
  draftId?: string;
  destinationKey: string;
  pickupAt?: string;
  returnAt?: string;
  billingCycles: number;
  driverEligible: boolean;
  selectionDisabled: boolean;
  disabledReason?: string | null;
  explanation?: string | null;
  availableTripBudgetCents?: number | null;
  sort: RentalSort;
  options: RentalOptionResponse[];
};

export type SelectAirfareRequest = {
  expectedVersion: number;
  expectedDraftVersion: number;
  outboundFlightInstanceId: number;
  returnFlightInstanceId: number;
};

export type SelectStayRequest = {
  expectedVersion: number;
  expectedDraftVersion: number;
  accommodationUnitId: number;
  unitCount: number;
};

export type SelectRentalRequest = {
  expectedVersion: number;
  expectedDraftVersion: number;
  rentalUnitId: number;
  pickupAt: string;
  returnAt: string;
};

export type AirfareSearchParams = {
  directOnly?: boolean;
  sort?: AirfareSort;
};

export type StaySearchParams = {
  type?: AccommodationType;
  sort?: StaySort;
};

export type RentalSearchParams = {
  pickupAt?: string;
  returnAt?: string;
  sort?: RentalSort;
};

export type ItineraryTallyResponse = {
  airfareTotalCents: number;
  stayTotalCents: number;
  rentalTotalCents: number;
  grandTotalCents: number;
  remainingBudgetCents: number | null;
  budgetOverageCents: number | null;
  isOverBudget: boolean;
};

export type DraftSelectionResponse = {
  airfare: AirfareComponentResponse | null;
  stay: StayComponentResponse | null;
  rental: RentalComponentResponse | null;
};

export type DraftResponse = {
  id: string;
  version: number;
  selections: DraftSelectionResponse;
  tally?: ItineraryTallyResponse;
};

export type PlannedResponse = {
  id: string;
  selections: DraftSelectionResponse;
  tally?: ItineraryTallyResponse;
};

export type AlternativeResponse = {
  id: string;
  lifecycle: 'DRAFT' | 'PLANNED' | string;
  version: number | null;
  selections: DraftSelectionResponse;
  tally?: ItineraryTallyResponse;
};

export type ComponentRemovalResponse = {
  draftId: string;
  component: string;
  reason: string;
};

export type ComponentAdjustmentResponse = {
  draftId: string;
  component: string;
  changeType: string;
  previousUnitCount: number | null;
  newUnitCount: number | null;
  previousPriceCents: number | null;
  newPriceCents: number | null;
  reason: string;
};

export type RevisionSummaryResponse = {
  removals: ComponentRemovalResponse[];
  adjustments: ComponentAdjustmentResponse[];
};

export type TripResponse = {
  id: string;
  destinationKey: string;
  destinationName: string;
  originAirportCode: string;
  startDate: string;
  endDate: string;
  travelerCount: number;
  travelerAges: number[] | null;
  budgetCents: number | null;
  label: string;
  version: number;
  drafts: DraftResponse[];
  planned: PlannedResponse[];
  alternatives: AlternativeResponse[];
  revisionSummary: RevisionSummaryResponse | null;
  tally?: ItineraryTallyResponse;
};

export type CreateTripRequest = {
  destinationKey: string;
  startDate: string;
  endDate: string;
  travelerCount: number;
  travelerAges?: number[] | null;
  budgetCents?: number | null;
};

export type SharedDetailsUpdateRequest = {
  expectedVersion: number;
  destinationKey: string;
  startDate: string;
  endDate: string;
  travelerCount: number;
  travelerAges?: number[] | null;
  budgetCents?: number | null;
};

export type TripRevisionRequest = {
  expectedVersion: number;
  destinationKey: string;
  startDate: string;
  endDate: string;
  travelerCount: number;
  travelerAges?: number[] | null;
  budgetCents?: number | null;
  sourcePlannedItineraryIds: string[];
};

export type DraftCreateRequest = {
  expectedVersion: number;
};

export type DraftMutationRequest = {
  expectedVersion: number;
  expectedDraftVersion: number;
};

export type AlternativeDuplicateRequest = {
  expectedVersion: number;
  expectedDraftVersion?: number | null;
};

export type AlternativeDeleteRequest = {
  expectedVersion: number;
  expectedDraftVersion?: number | null;
  confirmed?: boolean | null;
};

export type TripDeleteRequest = {
  expectedVersion: number;
  expectedDraftCount: number;
  expectedPlannedCount: number;
  confirmed?: boolean | null;
};

function cleanCreatePayload(p: CreateTripRequest): Record<string, unknown> {
  const body: Record<string, unknown> = {
    destinationKey: p.destinationKey,
    startDate: p.startDate,
    endDate: p.endDate,
    travelerCount: p.travelerCount,
  };
  if (p.travelerAges !== undefined) body.travelerAges = p.travelerAges;
  if (p.budgetCents !== undefined) body.budgetCents = p.budgetCents;
  return body;
}

function cleanUpdatePayload(p: SharedDetailsUpdateRequest): Record<string, unknown> {
  const body: Record<string, unknown> = {
    expectedVersion: p.expectedVersion,
    destinationKey: p.destinationKey,
    startDate: p.startDate,
    endDate: p.endDate,
    travelerCount: p.travelerCount,
  };
  if (p.travelerAges !== undefined) body.travelerAges = p.travelerAges;
  if (p.budgetCents !== undefined) body.budgetCents = p.budgetCents;
  return body;
}

function cleanRevisionPayload(p: TripRevisionRequest): Record<string, unknown> {
  const body: Record<string, unknown> = {
    expectedVersion: p.expectedVersion,
    destinationKey: p.destinationKey,
    startDate: p.startDate,
    endDate: p.endDate,
    travelerCount: p.travelerCount,
    sourcePlannedItineraryIds: p.sourcePlannedItineraryIds,
  };
  if (p.travelerAges !== undefined) body.travelerAges = p.travelerAges;
  if (p.budgetCents !== undefined) body.budgetCents = p.budgetCents;
  return body;
}

function cleanDraftCreatePayload(p: DraftCreateRequest): Record<string, unknown> {
  return { expectedVersion: p.expectedVersion };
}

function cleanDraftMutationPayload(p: DraftMutationRequest): Record<string, unknown> {
  return { expectedVersion: p.expectedVersion, expectedDraftVersion: p.expectedDraftVersion };
}

function cleanAlternativeDuplicatePayload(p: AlternativeDuplicateRequest): Record<string, unknown> {
  const body: Record<string, unknown> = { expectedVersion: p.expectedVersion };
  if (p.expectedDraftVersion !== undefined && p.expectedDraftVersion !== null) {
    body.expectedDraftVersion = p.expectedDraftVersion;
  }
  return body;
}

function cleanAlternativeDeletePayload(p: AlternativeDeleteRequest): Record<string, unknown> {
  const body: Record<string, unknown> = { expectedVersion: p.expectedVersion };
  if (p.expectedDraftVersion !== undefined && p.expectedDraftVersion !== null) {
    body.expectedDraftVersion = p.expectedDraftVersion;
  }
  if (p.confirmed !== undefined && p.confirmed !== null) {
    body.confirmed = p.confirmed;
  }
  return body;
}

function cleanTripDeletePayload(p: TripDeleteRequest): Record<string, unknown> {
  const body: Record<string, unknown> = {
    expectedVersion: p.expectedVersion,
    expectedDraftCount: p.expectedDraftCount,
    expectedPlannedCount: p.expectedPlannedCount,
  };
  if (p.confirmed !== undefined && p.confirmed !== null) {
    body.confirmed = p.confirmed;
  }
  return body;
}

function cleanSelectAirfarePayload(p: SelectAirfareRequest): Record<string, unknown> {
  return {
    expectedVersion: p.expectedVersion,
    expectedDraftVersion: p.expectedDraftVersion,
    outboundFlightInstanceId: p.outboundFlightInstanceId,
    returnFlightInstanceId: p.returnFlightInstanceId,
  };
}

function cleanSelectStayPayload(p: SelectStayRequest): Record<string, unknown> {
  return {
    expectedVersion: p.expectedVersion,
    expectedDraftVersion: p.expectedDraftVersion,
    accommodationUnitId: p.accommodationUnitId,
    unitCount: p.unitCount,
  };
}

function cleanSelectRentalPayload(p: SelectRentalRequest): Record<string, unknown> {
  return {
    expectedVersion: p.expectedVersion,
    expectedDraftVersion: p.expectedDraftVersion,
    rentalUnitId: p.rentalUnitId,
    pickupAt: p.pickupAt,
    returnAt: p.returnAt,
  };
}

function buildQueryString(params?: Record<string, string | number | boolean | undefined>): string {
  if (!params) return '';
  const searchParams = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') {
      searchParams.set(key, String(value));
    }
  }
  const qs = searchParams.toString();
  return qs ? `?${qs}` : '';
}

export const tripsApi = {
  createTrip: (payload: CreateTripRequest): Promise<TripResponse> =>
    request<TripResponse>('/api/trips', 'POST', cleanCreatePayload(payload)),

  listTrips: (): Promise<TripsProfileResponse> =>
    request<TripsProfileResponse>('/api/trips', 'GET'),

  getTrip: (tripId: string): Promise<TripResponse> =>
    request<TripResponse>(`/api/trips/${tripId}`, 'GET'),

  replaceSharedDetails: (tripId: string, payload: SharedDetailsUpdateRequest): Promise<TripResponse> =>
    request<TripResponse>(`/api/trips/${tripId}`, 'PUT', cleanUpdatePayload(payload)),

  duplicateTrip: (tripId: string, payload: TripRevisionRequest): Promise<TripResponse> =>
    request<TripResponse>(`/api/trips/${tripId}/duplicate`, 'POST', cleanRevisionPayload(payload)),

  createDraft: (tripId: string, payload: DraftCreateRequest): Promise<TripResponse> =>
    request<TripResponse>(`/api/trips/${tripId}/drafts`, 'POST', cleanDraftCreatePayload(payload)),

  duplicateDraft: (tripId: string, draftId: string, payload: DraftMutationRequest): Promise<TripResponse> =>
    request<TripResponse>(`/api/trips/${tripId}/drafts/${draftId}/duplicate`, 'POST', cleanDraftMutationPayload(payload)),

  duplicateAlternative: (tripId: string, alternativeId: string, payload: AlternativeDuplicateRequest): Promise<TripResponse> =>
    request<TripResponse>(`/api/trips/${tripId}/alternatives/${alternativeId}/duplicate`, 'POST', cleanAlternativeDuplicatePayload(payload)),

  deleteDraft: (tripId: string, draftId: string, payload: DraftMutationRequest): Promise<TripResponse> =>
    request<TripResponse>(`/api/trips/${tripId}/drafts/${draftId}`, 'DELETE', cleanDraftMutationPayload(payload)),

  deleteAlternative: (tripId: string, alternativeId: string, payload: AlternativeDeleteRequest): Promise<TripResponse> =>
    request<TripResponse>(`/api/trips/${tripId}/alternatives/${alternativeId}`, 'DELETE', cleanAlternativeDeletePayload(payload)),

  deleteTrip: (tripId: string, payload: TripDeleteRequest): Promise<void> =>
    request<void>(`/api/trips/${tripId}`, 'DELETE', cleanTripDeletePayload(payload)),

  searchAirfare: (tripId: string, draftId: string, params?: AirfareSearchParams): Promise<AirfareSearchResponse> =>
    request<AirfareSearchResponse>(`/api/trips/${tripId}/drafts/${draftId}/airfare${buildQueryString(params as Record<string, string | number | boolean | undefined>)}`, 'GET'),

  selectAirfare: (tripId: string, draftId: string, payload: SelectAirfareRequest): Promise<TripResponse> =>
    request<TripResponse>(`/api/trips/${tripId}/drafts/${draftId}/airfare`, 'PUT', cleanSelectAirfarePayload(payload)),

  removeAirfare: (tripId: string, draftId: string, payload: DraftMutationRequest): Promise<TripResponse> =>
    request<TripResponse>(`/api/trips/${tripId}/drafts/${draftId}/airfare`, 'DELETE', cleanDraftMutationPayload(payload)),

  searchStays: (tripId: string, draftId: string, params?: StaySearchParams): Promise<StaySearchResponse> =>
    request<StaySearchResponse>(`/api/trips/${tripId}/drafts/${draftId}/stays${buildQueryString(params as Record<string, string | number | boolean | undefined>)}`, 'GET'),

  selectStay: (tripId: string, draftId: string, payload: SelectStayRequest): Promise<TripResponse> =>
    request<TripResponse>(`/api/trips/${tripId}/drafts/${draftId}/stays`, 'PUT', cleanSelectStayPayload(payload)),

  removeStay: (tripId: string, draftId: string, payload: DraftMutationRequest): Promise<TripResponse> =>
    request<TripResponse>(`/api/trips/${tripId}/drafts/${draftId}/stays`, 'DELETE', cleanDraftMutationPayload(payload)),

  searchRentals: (tripId: string, draftId: string, params?: RentalSearchParams): Promise<RentalSearchResponse> =>
    request<RentalSearchResponse>(`/api/trips/${tripId}/drafts/${draftId}/rentals${buildQueryString(params as Record<string, string | number | boolean | undefined>)}`, 'GET'),

  selectRental: (tripId: string, draftId: string, payload: SelectRentalRequest): Promise<TripResponse> =>
    request<TripResponse>(`/api/trips/${tripId}/drafts/${draftId}/rentals`, 'PUT', cleanSelectRentalPayload(payload)),

  removeRental: (tripId: string, draftId: string, payload: DraftMutationRequest): Promise<TripResponse> =>
    request<TripResponse>(`/api/trips/${tripId}/drafts/${draftId}/rentals`, 'DELETE', cleanDraftMutationPayload(payload)),
};
