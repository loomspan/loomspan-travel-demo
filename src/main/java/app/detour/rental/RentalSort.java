package app.detour.rental;

import app.detour.api.ApiException;
import java.util.Map;

public enum RentalSort {
    DEFAULT,
    LOWEST_PRICE;

    public static RentalSort from(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }
        for (RentalSort sort : values()) {
            if (sort.name().equalsIgnoreCase(value.trim())) {
                return sort;
            }
        }
        throw new ApiException(400, "VALIDATION_FAILED", "One or more fields are invalid.",
                Map.of("sort", "Choose a supported sort option."));
    }
}
