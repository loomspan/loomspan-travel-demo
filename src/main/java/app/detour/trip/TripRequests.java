package app.detour.trip;

import app.detour.api.ApiException;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.List;

public final class TripRequests {
    private TripRequests() {
    }

    public record Create(String destinationKey, LocalDate startDate, LocalDate endDate, Integer travelerCount,
            List<Integer> travelerAges, JsonNode budgetCents) {
    }

    public record SharedDetailsUpdate(long expectedVersion, String destinationKey, LocalDate startDate, LocalDate endDate,
            Integer travelerCount, List<Integer> travelerAges, JsonNode budgetCents) { }

    public record DraftCreate(long expectedVersion) { }

    public record DraftMutation(long expectedVersion, long expectedDraftVersion) { }

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

    static DraftCreate draftCreate(JsonNode body) {
        requireObject(body, Set.of("expectedVersion"));
        return new DraftCreate(version(body.get("expectedVersion"), "expectedVersion"));
    }

    static DraftMutation draftMutation(JsonNode body) {
        requireObject(body, Set.of("expectedVersion", "expectedDraftVersion"));
        return new DraftMutation(version(body.get("expectedVersion"), "expectedVersion"), version(body.get("expectedDraftVersion"), "expectedDraftVersion"));
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

    private static List<Integer> ages(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (!node.isArray()) throw invalid("travelerAges", "Traveler ages must be an array.");
        List<Integer> values = new ArrayList<>();
        for (int index = 0; index < node.size(); index++) values.add(integer(node.get(index), "travelerAges"));
        return values;
    }

    private static ApiException invalid(String field, String message) {
        return new ApiException(400, "VALIDATION_FAILED", "One or more fields are invalid.", Map.of(field, message));
    }
}
