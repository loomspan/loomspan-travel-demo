ALTER TABLE flight_instance
    ADD CONSTRAINT ck_flight_instance_total_fare
    CHECK (base_fare_cents <= 9223372036854775807 - tax_cents - fee_cents);

ALTER TABLE accommodation_nightly_inventory
    ADD CONSTRAINT ck_accommodation_nightly_total_price
    CHECK (base_price_cents <= 9223372036854775807 - tax_cents - fee_cents);

ALTER TABLE rental_vehicle_class
    ADD CONSTRAINT ck_rental_vehicle_class_total_daily_price
    CHECK (daily_base_price_cents <= 9223372036854775807 - daily_tax_cents - daily_fee_cents);
