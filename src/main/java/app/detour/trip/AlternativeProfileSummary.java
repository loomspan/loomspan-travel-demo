package app.detour.trip;

import java.util.UUID;
import java.time.LocalDate;

public record AlternativeProfileSummary(UUID id, String lifecycle, Long version, String status, boolean expired,
        String name, LocalDate startDate, LocalDate endDate, boolean booked) {
    public AlternativeProfileSummary(UUID id, String lifecycle, Long version, String status, boolean expired,
            String name, LocalDate startDate, LocalDate endDate) {
        this(id, lifecycle, version, status, expired, name, startDate, endDate, false);
    }
    public AlternativeProfileSummary(UUID id, String lifecycle, Long version, String status, boolean expired) {
        this(id, lifecycle, version, status, expired, null, null, null, false);
    }
}
