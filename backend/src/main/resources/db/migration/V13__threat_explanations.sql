-- 11.10: "why is this risky?" (English + Hindi, max two sentences) kept with the threat for the history screen.
ALTER TABLE threat_events ADD COLUMN explanation_en VARCHAR(600);
ALTER TABLE threat_events ADD COLUMN explanation_hi VARCHAR(600);
