package app.detour.trip;

import java.time.LocalDate;

public record StayNight(LocalDate date, long basePriceCents, long taxCents, long feeCents) {
}
