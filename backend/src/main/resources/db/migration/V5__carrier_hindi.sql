-- 3.4.5: Hindi / mixed-language answers and phone numbers written with spaces or dashes ("+91 98765 43210").
-- Still generic wording (not real captures); mirrored in mobile/lib/core/carrier_classifier.dart builtInRules
-- and checked against the shared corpus mobile/test/fixtures/ussd_responses.json by CarrierCorpusTest.
UPDATE carrier_patterns SET regex = '\+?\d(?:[\s-]?\d){9,14}', updated_at = now()
 WHERE country = '*' AND carrier = '*' AND kind = 'ACTIVE' AND priority = 30 AND regex = '\+?\d{10,15}';

INSERT INTO carrier_patterns (kind, regex, priority) VALUES
 ('INACTIVE', '(निष्क्रिय|बंद|सक्रिय\s*नहीं)', 13),
 ('ACTIVE',   'सक्रिय',                         22);
