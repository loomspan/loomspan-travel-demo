ALTER TABLE flight_schedule_segment
    ADD CONSTRAINT ck_flight_schedule_segment_local_times
    CHECK (arrival_day_offset > 0 OR arrival_local_time > departure_local_time);

CREATE TRIGGER catalog_destination_key_immutable
BEFORE UPDATE ON catalog_destination
FOR EACH ROW CALL "app.detour.catalog.persistence.ImmutableCatalogKeyTrigger";

CREATE TRIGGER catalog_airport_key_immutable
BEFORE UPDATE ON catalog_airport
FOR EACH ROW CALL "app.detour.catalog.persistence.ImmutableCatalogKeyTrigger";

CREATE TRIGGER catalog_supplier_key_immutable
BEFORE UPDATE ON catalog_supplier
FOR EACH ROW CALL "app.detour.catalog.persistence.ImmutableCatalogKeyTrigger";

CREATE TRIGGER flight_schedule_key_immutable
BEFORE UPDATE ON flight_schedule
FOR EACH ROW CALL "app.detour.catalog.persistence.ImmutableCatalogKeyTrigger";

CREATE TRIGGER flight_instance_key_immutable
BEFORE UPDATE ON flight_instance
FOR EACH ROW CALL "app.detour.catalog.persistence.ImmutableCatalogKeyTrigger";

CREATE TRIGGER accommodation_property_key_immutable
BEFORE UPDATE ON accommodation_property
FOR EACH ROW CALL "app.detour.catalog.persistence.ImmutableCatalogKeyTrigger";

CREATE TRIGGER accommodation_unit_key_immutable
BEFORE UPDATE ON accommodation_unit
FOR EACH ROW CALL "app.detour.catalog.persistence.ImmutableCatalogKeyTrigger";

CREATE TRIGGER rental_location_key_immutable
BEFORE UPDATE ON rental_location
FOR EACH ROW CALL "app.detour.catalog.persistence.ImmutableCatalogKeyTrigger";

CREATE TRIGGER rental_vehicle_class_key_immutable
BEFORE UPDATE ON rental_vehicle_class
FOR EACH ROW CALL "app.detour.catalog.persistence.ImmutableCatalogKeyTrigger";

CREATE TRIGGER rental_unit_key_immutable
BEFORE UPDATE ON rental_unit
FOR EACH ROW CALL "app.detour.catalog.persistence.ImmutableCatalogKeyTrigger";
