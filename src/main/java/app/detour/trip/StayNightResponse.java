package app.detour.trip;

import java.time.LocalDate;

public record StayNightResponse(LocalDate date, long basePriceCents, long taxCents, long feeCents) {
}
