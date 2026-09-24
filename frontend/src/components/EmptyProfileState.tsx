type EmptyProfileStateProps = {
  onPlanTrip?: () => void;
  onStartPlanTrip?: () => void;
  onStartAirfare?: () => void;
  onStartStay?: () => void;
};

export function EmptyProfileState({
  onPlanTrip,
  onStartPlanTrip,
  onStartAirfare,
  onStartStay,
}: EmptyProfileStateProps) {
  const handlePlanTrip = onStartPlanTrip ?? onPlanTrip;

  return (
    <section className="empty-state" aria-labelledby="empty-heading">
      <h2 id="empty-heading">Your profile is ready</h2>
      <p>
        Trips let you organize and compare travel options from Portland (PDX) to San Francisco, Munich, or Mexico City.
        Create draft alternatives to explore itineraries, autosave your details, and prepare your travel plans.
      </p>
      <div className="empty-state-actions">
        <button type="button" className="primary" onClick={handlePlanTrip}>
          Plan Trip
        </button>
        <button
          type="button"
          className="secondary"
          onClick={onStartAirfare ?? handlePlanTrip}
        >
          Airfare
        </button>
        <button
          type="button"
          className="secondary"
          onClick={onStartStay ?? handlePlanTrip}
        >
          Stay
        </button>
      </div>
    </section>
  );
}
