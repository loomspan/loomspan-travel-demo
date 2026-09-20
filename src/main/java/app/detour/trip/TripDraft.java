package app.detour.trip;

import java.util.UUID;

record TripDraft(long id, UUID publicId, long version) {
}
