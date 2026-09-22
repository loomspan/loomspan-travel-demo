package app.detour.stay;

import app.detour.api.ApiException;
import java.util.Map;

public enum AccommodationType {
    HOTEL,
    BED_AND_BREAKFAST,
    VACATION_RENTAL;

    public static AccommodationType from(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(400, "VALIDATION_FAILED", "One or more fields are invalid.",
                    Map.of("type", "Choose a supported accommodation type."));
        }
        for (AccommodationType type : values()) {
            if (type.name().equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        throw new ApiException(400, "VALIDATION_FAILED", "One or more fields are invalid.",
                    Map.of("type", "Choose a supported accommodation type."));
    }
}
