package app.detour.trip;

import app.detour.api.ApiException;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public final class TripRequests {
    private TripRequests() {
    }

    public record Create(String destinationKey, LocalDate startDate, LocalDate endDate, Integer travelerCount,
            List<Integer> travelerAges, JsonNode budgetCents) {
    }

    public record SharedDetailsUpdate(long expectedVersion, String destinationKey, LocalDate startDate, LocalDate endDate,
            Integer travelerCount, List<Integer> travelerAges, JsonNode budgetCents) { }

    public record TripRevision(long expectedVersion, String destinationKey, LocalDate startDate, LocalDate endDate,
            Integer travelerCount, List<Integer> travelerAges, JsonNode budgetCents, List<UUID> sourcePlannedItineraryIds) { }

    public record DraftCreate(long expectedVersion) { }

    public record DraftMutation(long expectedVersion, long expectedDraftVersion) { }
    public record AirfareSelectionRequest(long expectedVersion, long expectedDraftVersion,
            long outboundFlightInstanceId, long returnFlightInstanceId) { }
    public record StaySelectionRequest(long expectedVersion, long expectedDraftVersion,
            long accommodationUnitId, Integer unitCount) { }
    public record RentalSelectionRequest(long expectedVersion, long expectedDraftVersion,
            long rentalUnitId, OffsetDateTime pickupAt, OffsetDateTime returnAt) { }
    public record Promotion(long expectedVersion, long expectedDraftVersion, Boolean budgetOverageAcknowledged) {
        public Promotion(long expectedVersion, long expectedDraftVersion) {
            this(expectedVersion, expectedDraftVersion, null);
        }
    }
    public record AlternativeDuplicate(long expectedVersion, Long expectedDraftVersion) { }
    public record AlternativeDelete(long expectedVersion, Long expectedDraftVersion, Boolean confirmed) { }
    public record TripDelete(long expectedVersion, int expectedDraftCount, int expectedPlannedCount, Boolean confirmed) { }
    public record BookingCreate(UUID plannedItineraryId, long expectedVersion, String idempotencyKey) { }
    public record Cancel(long expectedVersion) { }

    static Create from(JsonNode body) {
        requireObject(body, Set.of("destinationKey", "startDate", "endDate", "travelerCount", "travelerAges", "budgetCents"));
        return new Create(text(body.get("destinationKey"), "destinationKey"),
                date(body.get("startDate"), "startDate"), date(body.get("endDate"), "endDate"), integer(body.get("travelerCount"), "travelerCount"),
                ages(body.get("travelerAges")), body.get("budgetCents"));
    }

    static SharedDetailsUpdate update(JsonNode body) {
        requireObject(body, Set.of("expectedVersion", "destinationKey", "startDate", "endDate", "travelerCount", "travelerAges", "budgetCents"));
        return new SharedDetailsUpdate(version(body.get("expectedVersion"), "expectedVersion"), text(body.get("destinationKey"), "destinationKey"),
                date(body.get("startDate"), "startDate"), date(body.get("endDate"), "endDate"), integer(body.get("travelerCount"), "travelerCount"),
                ages(body.get("travelerAges")), body.get("budgetCents"));
    }

    static TripRevision revision(JsonNode body) {
        requireObject(body, Set.of("expectedVersion", "destinationKey", "startDate", "endDate", "travelerCount", "travelerAges", "budgetCents", "sourcePlannedItineraryIds"));
        return new TripRevision(version(body.get("expectedVersion"), "expectedVersion"), text(body.get("destinationKey"), "destinationKey"),
                date(body.get("startDate"), "startDate"), date(body.get("endDate"), "endDate"), integer(body.get("travelerCount"), "travelerCount"),
                ages(body.get("travelerAges")), body.get("budgetCents"), sourcePlannedIds(body.get("sourcePlannedItineraryIds")));
    }

    static DraftCreate draftCreate(JsonNode body) {
        requireObject(body, Set.of("expectedVersion"));
        return new DraftCreate(version(body.get("expectedVersion"), "expectedVersion"));
    }

    static DraftMutation draftMutation(JsonNode body) {
        requireObject(body, Set.of("expectedVersion", "expectedDraftVersion"));
        return new DraftMutation(version(body.get("expectedVersion"), "expectedVersion"), version(body.get("expectedDraftVersion"), "expectedDraftVersion"));
    }

    static AirfareSelectionRequest airfareSelection(JsonNode body) {
        requireObject(body, Set.of("expectedVersion", "expectedDraftVersion", "outboundFlightInstanceId", "returnFlightInstanceId"));
        return new AirfareSelectionRequest(
                version(body.get("expectedVersion"), "expectedVersion"),
                version(body.get("expectedDraftVersion"), "expectedDraftVersion"),
                positiveLong(body.get("outboundFlightInstanceId"), "outboundFlightInstanceId"),
                positiveLong(body.get("returnFlightInstanceId"), "returnFlightInstanceId"));
    }

    static StaySelectionRequest staySelection(JsonNode body) {
        requireObject(body, Set.of("expectedVersion", "expectedDraftVersion", "accommodationUnitId", "unitCount"));
        return new StaySelectionRequest(
                version(body.get("expectedVersion"), "expectedVersion"),
                version(body.get("expectedDraftVersion"), "expectedDraftVersion"),
                positiveLong(body.get("accommodationUnitId"), "accommodationUnitId"),
                positiveInt(body.get("unitCount"), "unitCount"));
    }

    static RentalSelectionRequest rentalSelection(JsonNode body) {
        requireObject(body, Set.of("expectedVersion", "expectedDraftVersion", "rentalUnitId", "pickupAt", "returnAt"));
        return new RentalSelectionRequest(
                version(body.get("expectedVersion"), "expectedVersion"),
                version(body.get("expectedDraftVersion"), "expectedDraftVersion"),
                positiveLong(body.get("rentalUnitId"), "rentalUnitId"),
                offsetDateTime(body.get("pickupAt"), "pickupAt"),
                offsetDateTime(body.get("returnAt"), "returnAt"));
    }

    static Promotion promotion(JsonNode body) {
        requireObject(body, Set.of("expectedVersion", "expectedDraftVersion", "budgetOverageAcknowledged"));
        Boolean acknowledged = (body.get("budgetOverageAcknowledged") == null || body.get("budgetOverageAcknowledged").isNull())
                ? null : booleanValue(body.get("budgetOverageAcknowledged"), "budgetOverageAcknowledged");
        return new Promotion(
                version(body.get("expectedVersion"), "expectedVersion"),
                version(body.get("expectedDraftVersion"), "expectedDraftVersion"),
                acknowledged);
    }

    static AlternativeDuplicate alternativeDuplicate(JsonNode body) {
        requireObject(body, Set.of("expectedVersion", "expectedDraftVersion"));
        return new AlternativeDuplicate(version(body.get("expectedVersion"), "expectedVersion"), optionalVersion(body.get("expectedDraftVersion"), "expectedDraftVersion"));
    }

    static AlternativeDelete alternativeDelete(JsonNode body) {
        requireObject(body, Set.of("expectedVersion", "expectedDraftVersion", "confirmed"));
        Boolean confirmed = body.get("confirmed") == null || body.get("confirmed").isNull() ? null : booleanValue(body.get("confirmed"), "confirmed");
        return new AlternativeDelete(version(body.get("expectedVersion"), "expectedVersion"), optionalVersion(body.get("expectedDraftVersion"), "expectedDraftVersion"), confirmed);
    }

    static TripDelete tripDelete(JsonNode body) {
        requireObject(body, Set.of("expectedVersion", "expectedDraftCount", "expectedPlannedCount", "confirmed"));
        Boolean confirmed = body.get("confirmed") == null || body.get("confirmed").isNull() ? null : booleanValue(body.get("confirmed"), "confirmed");
        return new TripDelete(version(body.get("expectedVersion"), "expectedVersion"),
                nonNegativeInt(body.get("expectedDraftCount"), "expectedDraftCount"),
                nonNegativeInt(body.get("expectedPlannedCount"), "expectedPlannedCount"),
                confirmed);
    }

    public static BookingCreate booking(JsonNode body, String headerIdempotencyKey) {
        requireObject(body, Set.of("plannedItineraryId", "expectedVersion", "idempotencyKey"));
        UUID plannedItineraryId = uuid(body.get("plannedItineraryId"), "plannedItineraryId");
        long expectedVersion = version(body.get("expectedVersion"), "expectedVersion");
        String bodyKey = text(body.get("idempotencyKey"), "idempotencyKey");
        String idempotencyKey = (bodyKey != null && !bodyKey.isBlank()) ? bodyKey : headerIdempotencyKey;
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw invalid("idempotencyKey", "An idempotency key is required.");
        }
        return new BookingCreate(plannedItineraryId, expectedVersion, idempotencyKey);
    }

    public static Cancel cancel(JsonNode body) {
        requireObject(body, Set.of("expectedVersion"));
        return new Cancel(version(body.get("expectedVersion"), "expectedVersion"));
    }

    private static void requireObject(JsonNode body, Set<String> allowed) {
        if (body == null || !body.isObject()) throw invalid("request", "A JSON object is required.");
        for (var property : body.properties()) {
            if (!allowed.contains(property.getKey())) throw invalid("request", "The request contains an unsupported field.");
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isNull()) return null;
        if (!node.isTextual()) throw invalid(field, "This field must be a string.");
        return node.asString();
    }

    private static LocalDate date(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) return null;
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw invalid(field, "Enter an ISO date.");
        }
    }

    private static OffsetDateTime offsetDateTime(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) throw invalid(field, "This field is required.");
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException exception) {
            try {
                return java.time.LocalDateTime.parse(value).atOffset(java.time.ZoneOffset.UTC);
            } catch (DateTimeParseException ignored) {
                throw invalid(field, "Enter a valid ISO timestamp.");
            }
        }
    }

    private static Integer integer(JsonNode node, String field) {
        if (node == null || node.isNull()) return null;
        if (!node.isInt()) throw invalid(field, "This field must be an integer.");
        return node.asInt();
    }

    private static long version(JsonNode node, String field) {
        if (node == null || node.isNull()) throw invalid(field, "This field is required.");
        if (!node.isIntegralNumber() || !node.canConvertToLong() || node.longValue() < 0) throw invalid(field, "This field must be a non-negative integer.");
        return node.longValue();
    }
    private static long positiveLong(JsonNode node, String field) {
        if (node == null || node.isNull()) throw invalid(field, "This field is required.");
        if (!node.isIntegralNumber() || !node.canConvertToLong() || node.longValue() <= 0) throw invalid(field, "This field must be a positive integer.");
        return node.longValue();
    }
    private static Integer positiveInt(JsonNode node, String field) {
        if (node == null || node.isNull()) return null;
        if (!node.isIntegralNumber() || !node.canConvertToInt() || node.intValue() <= 0) throw invalid(field, "This field must be a positive integer.");
        return node.intValue();
    }
    private static Long optionalVersion(JsonNode node, String field) { return node == null || node.isNull() ? null : version(node, field); }
    private static int nonNegativeInt(JsonNode node, String field) {
        if (node == null || node.isNull()) throw invalid(field, "This field is required.");
        if (!node.isIntegralNumber() || !node.canConvertToInt() || node.intValue() < 0) throw invalid(field, "This field must be a non-negative integer.");
        return node.intValue();
    }
    private static boolean booleanValue(JsonNode node, String field) {
        if (!node.isBoolean()) throw invalid(field, "This field must be true or false.");
        return node.asBoolean();
    }

    private static List<Integer> ages(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (!node.isArray()) throw invalid("travelerAges", "Traveler ages must be an array.");
        List<Integer> values = new ArrayList<>();
        for (int index = 0; index < node.size(); index++) values.add(integer(node.get(index), "travelerAges"));
        return values;
    }

    private static List<UUID> sourcePlannedIds(JsonNode node) {
        if (node == null || node.isNull()) throw invalid("sourcePlannedItineraryIds", "Select at least one Planned alternative to copy.");
        if (!node.isArray()) throw invalid("sourcePlannedItineraryIds", "This field must be an array of alternative identifiers.");
        if (node.isEmpty()) throw invalid("sourcePlannedItineraryIds", "Select at least one Planned alternative to copy.");
        List<UUID> ids = new ArrayList<>();
        java.util.Set<UUID> seen = new java.util.HashSet<>();
        for (int index = 0; index < node.size(); index++) {
            JsonNode element = node.get(index);
            if (element == null || !element.isTextual()) throw invalid("sourcePlannedItineraryIds", "Enter valid alternative identifiers.");
            UUID id;
            try {
                id = UUID.fromString(element.asString());
            } catch (IllegalArgumentException ex) {
                throw invalid("sourcePlannedItineraryIds", "Enter valid alternative identifiers.");
            }
            if (!seen.add(id)) {
                throw invalid("sourcePlannedItineraryIds", "Duplicate source alternatives are not allowed.");
            }
            ids.add(id);
        }
        return List.copyOf(ids);
    }

    private static UUID uuid(JsonNode node, String field) {
        String text = text(node, field);
        if (text == null || text.isBlank()) throw invalid(field, "This field is required.");
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException ex) {
            throw invalid(field, "Enter a valid UUID.");
        }
    }

    private static ApiException invalid(String field, String message) {
        return new ApiException(400, "VALIDATION_FAILED", "One or more fields are invalid.", Map.of(field, message));
    }
}
