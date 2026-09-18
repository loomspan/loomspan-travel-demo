ALTER TABLE accommodation_property
    ADD latitude DECIMAL(8, 5) NOT NULL;

ALTER TABLE accommodation_property
    ADD longitude DECIMAL(8, 5) NOT NULL;

ALTER TABLE accommodation_property
    ADD location_description VARCHAR(500) NOT NULL;

ALTER TABLE accommodation_property
    ADD guest_rating DECIMAL(2, 1) NOT NULL;

ALTER TABLE accommodation_property
    ADD distance_to_city_center_meters INTEGER NOT NULL;

ALTER TABLE accommodation_property
    ADD CONSTRAINT ck_accommodation_property_latitude CHECK (latitude BETWEEN -90 AND 90);

ALTER TABLE accommodation_property
    ADD CONSTRAINT ck_accommodation_property_longitude CHECK (longitude BETWEEN -180 AND 180);

ALTER TABLE accommodation_property
    ADD CONSTRAINT ck_accommodation_property_location_description CHECK (CHAR_LENGTH(location_description) > 0);

ALTER TABLE accommodation_property
    ADD CONSTRAINT ck_accommodation_property_guest_rating CHECK (guest_rating BETWEEN 0 AND 5);

ALTER TABLE accommodation_property
    ADD CONSTRAINT ck_accommodation_property_distance CHECK (distance_to_city_center_meters >= 0);
