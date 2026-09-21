package app.detour.trip;

import java.util.UUID;

public record AlternativeProfileSummary(UUID id, String lifecycle, Long version, String status, boolean expired) {
}
