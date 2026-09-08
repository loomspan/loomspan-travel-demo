-- Fictional, finite demo inventory. See docs/first-demo-scenario.md.
INSERT INTO travel_service VALUES ('RAIL-OUT', '{"id":"RAIL-OUT","mode":"rail","direction":"outbound","departs":"2026-10-16T10:00:00-04:00","arrives":"2026-10-16T14:00:00-04:00","farePerPersonCents":9500,"seats":2}', 2);
INSERT INTO travel_service VALUES ('AIR-OUT', '{"id":"AIR-OUT","mode":"flight","direction":"outbound","departs":"2026-10-16T13:00:00-04:00","arrives":"2026-10-16T14:10:00-04:00","farePerPersonCents":6500,"seats":6}', 6);
INSERT INTO travel_service VALUES ('AIR-LATE', '{"id":"AIR-LATE","mode":"flight","direction":"outbound","departs":"2026-10-16T15:00:00-04:00","arrives":"2026-10-16T16:10:00-04:00","farePerPersonCents":4500,"seats":6}', 6);
INSERT INTO travel_service VALUES ('RAIL-RETURN', '{"id":"RAIL-RETURN","mode":"rail","direction":"return","departs":"2026-10-18T18:00:00-04:00","arrives":"2026-10-18T22:00:00-04:00","farePerPersonCents":8500,"seats":2}', 2);
INSERT INTO travel_service VALUES ('AIR-RETURN', '{"id":"AIR-RETURN","mode":"flight","direction":"return","departs":"2026-10-18T19:00:00-04:00","arrives":"2026-10-18T20:10:00-04:00","farePerPersonCents":6500,"seats":6}', 6);
INSERT INTO hotel VALUES ('CENTRAL', '{"id":"CENTRAL","name":"Central House","nightlyRoomCents":22000,"roomCapacity":2,"roomsPerNight":3,"checkIn":"15:00","quietRoom":false,"roomDescription":"Compact double room; no quiet-room guarantee","transfers":{"rail":{"minutes":20,"partyCents":2000},"flight":{"minutes":75,"partyCents":9000}}}');
INSERT INTO hotel_night VALUES ('CENTRAL', '2026-10-16', 3);
INSERT INTO hotel_night VALUES ('CENTRAL', '2026-10-17', 3);
INSERT INTO hotel VALUES ('GARDEN', '{"id":"GARDEN","name":"Garden Court","nightlyRoomCents":29000,"roomCapacity":2,"roomsPerNight":1,"checkIn":"15:00","quietRoom":true,"roomDescription":"Courtyard-facing king room with a guaranteed quiet-room allocation","transfers":{"rail":{"minutes":15,"partyCents":2000},"flight":{"minutes":65,"partyCents":9000}}}');
INSERT INTO hotel_night VALUES ('GARDEN', '2026-10-16', 1);
INSERT INTO hotel_night VALUES ('GARDEN', '2026-10-17', 1);
INSERT INTO hotel VALUES ('RIVERSIDE', '{"id":"RIVERSIDE","name":"Riverside Lodge","nightlyRoomCents":18000,"roomCapacity":2,"roomsPerNight":4,"checkIn":"15:00","quietRoom":false,"roomDescription":"Spacious double room; no quiet-room guarantee","transfers":{"rail":{"minutes":60,"partyCents":7000},"flight":{"minutes":90,"partyCents":11000}}}');
INSERT INTO hotel_night VALUES ('RIVERSIDE', '2026-10-16', 4);
INSERT INTO hotel_night VALUES ('RIVERSIDE', '2026-10-17', 4);
INSERT INTO travel_rules VALUES (1, '{"rail":{"departureBufferMinutes":20,"arrivalBufferMinutes":15,"bostonTransferMinutes":0,"bostonTransferPartyCents":0},"flight":{"departureBufferMinutes":90,"arrivalBufferMinutes":30,"bostonTransferMinutes":30,"bostonTransferPartyCents":4000}}');
