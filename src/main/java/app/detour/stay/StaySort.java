package app.detour.stay;

import app.detour.api.ApiException;
import java.util.Map;

public enum StaySort {
    DEFAULT,
    LOWEST_PRICE,
    HIGHEST_RATING,
    NEAREST_CITY_CENTER;

    public static StaySort from(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }
        for (StaySort sort : values()) {
            if (sort.name().equalsIgnoreCase(value.trim())) {
                return sort;
            }
        }
        throw new ApiException(400, "VALIDATION_FAILED", "One or more fields are invalid.",
                Map.of("sort", "Choose a supported sort option."));
    }
}
