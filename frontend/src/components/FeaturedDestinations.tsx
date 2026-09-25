const destinations = [
  {name: 'San Francisco', detail: 'California, United States', image: '/images/destinations/san-francisco.webp', alt: 'Golden Gate Bridge across San Francisco Bay'},
  {name: 'Munich', detail: 'Bavaria, Germany', image: '/images/destinations/munich.webp', alt: 'Marienplatz and the Neues Rathaus in Munich'},
  {name: 'Mexico City', detail: 'Mexico', image: '/images/destinations/mexico-city.webp', alt: 'Palacio de Bellas Artes in Mexico City'},
] as const;

export function FeaturedDestinations() {
  return <section className="featured-destinations" aria-labelledby="featured-heading">
    <div className="featured-heading">
      <div><p className="eyebrow">WHERE TO NEXT</p><h2 id="featured-heading">Featured destinations</h2></div>
      <p>Get inspired by the places you can explore with DeTour.</p>
    </div>
    <div className="destination-grid">
      {destinations.map((destination) => <article className="destination-card" key={destination.name}>
        <img src={destination.image} alt={destination.alt} width="960" height="640" loading="lazy" decoding="async" />
        <div className="destination-copy"><h3>{destination.name}</h3><p>{destination.detail}</p></div>
      </article>)}
    </div>
  </section>;
}
