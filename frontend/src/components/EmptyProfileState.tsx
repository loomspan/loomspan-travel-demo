type EmptyProfileStateProps = {
  onPlanTrip: () => void;
};

export function EmptyProfileState({onPlanTrip}: EmptyProfileStateProps) {
  return (
    <section className="empty-state" aria-labelledby="empty-heading">
      <h2 id="empty-heading">Your profile is ready</h2>
      <p>
        Trips let you organize and compare travel options from Portland (PDX) to San Francisco, Munich, or Mexico City.
        Create draft alternatives to explore itineraries, autosave your details, and prepare your travel plans.
      </p>
      <button type="button" className="primary" onClick={onPlanTrip}>
        Plan Trip
      </button>
    </section>
  );
}
