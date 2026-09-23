package app.detour.trip;

public record DraftSelections(AirfareSelection airfare, StaySelection stay, RentalSelection rental) {
    public static final DraftSelections EMPTY = new DraftSelections(null, null, null);
}
