package app.detour.trip;

import java.util.List;

public record StayComponentResponse(long accommodationUnitId, int unitCount, String propertyName, String unitName,
        List<StayNightResponse> nights, String propertyCategory, String locationDescription,
        Integer distanceToCityCenterMeters, Integer guestCapacity, Integer requiredRoomCount) {
    public StayComponentResponse(long accommodationUnitId, int unitCount, String propertyName, String unitName,
            List<StayNightResponse> nights) {
        this(accommodationUnitId, unitCount, propertyName, unitName, nights, null, null, null, null, null);
    }
}
