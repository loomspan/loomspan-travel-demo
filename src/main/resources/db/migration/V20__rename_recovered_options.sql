-- V19 converted populated surplus Drafts into Saved options using an old-flow label.
-- Rename only that generated pattern; existing user-supplied option names stay intact.
UPDATE detour_planned_itinerary
SET name = REGEXP_REPLACE(name, '^Option from Draft ', 'Recovered option ')
WHERE REGEXP_LIKE(name, '^Option from Draft [0-9]+$');
