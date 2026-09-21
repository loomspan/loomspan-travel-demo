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

export type DraftSelectionResponse = {
  airfare: AirfareComponentResponse | null;
  stay: StayComponentResponse | null;
  rental: RentalComponentResponse | null;
};

export type DraftResponse = {
  id: string;
  selections: DraftSelectionResponse;
};

export type PlannedResponse = {
  id: string;
  selections: DraftSelectionResponse;
};

export type AlternativeResponse = {
  id: string;
  lifecycle: 'DRAFT' | 'PLANNED' | string;
  version: number | null;
  selections: DraftSelectionResponse;
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
};
