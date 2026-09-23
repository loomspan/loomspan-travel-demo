package app.detour.trip;

import java.util.List;

public record StaySelection(long accommodationUnitId, int unitCount, String propertyName, String unitName,
        List<StayNight> nights, String propertyCategory, String locationDescription,
        Integer distanceToCityCenterMeters, Integer guestCapacity, Integer requiredRoomCount) {
    public StaySelection(long accommodationUnitId, int unitCount, String propertyName, String unitName,
            List<StayNight> nights) {
        this(accommodationUnitId, unitCount, propertyName, unitName, nights, null, null, null, null, null);
    }
}
