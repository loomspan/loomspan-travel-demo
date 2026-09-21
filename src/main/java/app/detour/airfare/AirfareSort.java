package app.detour.airfare;

import app.detour.api.ApiException;
import java.util.Map;

public enum AirfareSort {
    DEFAULT,
    LOWEST_PRICE,
    SHORTEST_DURATION,
    EARLIEST_DEPARTURE,
    FEWEST_STOPS;

    public static AirfareSort from(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }
        for (AirfareSort sort : values()) {
            if (sort.name().equalsIgnoreCase(value.trim())) {
                return sort;
            }
        }
        throw new ApiException(400, "VALIDATION_FAILED", "One or more fields are invalid.",
                Map.of("sort", "Choose a supported sort option."));
    }
}
