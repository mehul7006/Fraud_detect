package com.fraudshield.safety;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fraudshield.lookalike.BrandRegistry;
import com.fraudshield.lookalike.LookalikeService;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 15.9: a fake support number for a known brand is flagged; the official one is not. */
class CareNumberServiceTest {
    private final BrandRegistry registry = LookalikeService.defaults().registry();
    private final java.util.Set<String> reportedNumbers = new java.util.HashSet<>();
    private CareNumberService care;

    @BeforeEach
    void setUp() {
        care = new CareNumberService(registry, raw -> reportedNumbers.contains(SafetyValues.number(raw)), CareNumberService.load());
    }

    @Test
    void everyDirectoryRowIsFromTheBrandsOwnWebsite() {
        var dir = care.directory();
        assertTrue(dir.values().stream().mapToInt(java.util.List::size).sum() >= 50);
        dir.forEach((brand, rows) -> {
            for (var e : rows) {
                URI u = URI.create(e.sourceUrl());
                assertEquals("https", u.getScheme(), e.toString());
                String host = u.getHost().replaceFirst("^www\\.", "");
                if (host.endsWith(".gov.in")) continue; // the 1930 government helpline
                var b = registry.brands().stream().filter(x -> x.name().equals(brand)).findFirst()
                        .orElseThrow(() -> new AssertionError("brand not in the brand list: " + brand));
                assertTrue(b.officialHosts().stream().anyMatch(h -> host.equals(h) || host.endsWith("." + h)),
                        e.sourceUrl() + " is not an official domain of " + brand);
                assertTrue(e.number().length() >= 3, e.toString());
            }
        });
    }

    @Test
    void officialNumbersInAnyWritingAreOfficial() {
        for (String n : new String[]{"1800 1234", "18001234", "1800-1234"}) {
            var r = care.check(n, "SBI");
            assertTrue(r.official(), n);
            assertEquals("OFFICIAL", r.verdict());
            assertEquals("State Bank of India", r.brand());
            assertTrue(r.sourceUrl().startsWith("https://sbi.bank.in/"));
        }
        assertTrue(care.check("080-26599990", null).official(), "STD landline with trunk 0");
        assertTrue(care.check("+91 80 2659 9990", "State Bank of India").official());
        assertTrue(care.check("+91-022 6827 6827", "LIC").official());
        assertTrue(care.check("1930", null).official());
        assertEquals("ICICI Bank", care.check("1800 1080", null).brand());
        // a bank's own WhatsApp / SMS mobile number is official, not "a personal mobile"
        assertTrue(care.check("94491 12211", "sbi").official());
        assertTrue(care.check("7000770007", "Jio").official());
    }

    @Test
    void fakeNumbersForAKnownBrandAreFlagged() {
        var fake = care.check("+91 98765 43210", "SBI");
        assertFalse(fake.official());
        assertEquals("FAKE", fake.verdict());
        assertTrue(fake.reasonCodes().contains("PERSONAL_MOBILE_FOR_BRAND"));
        assertFalse(fake.officialNumbers().isEmpty(), "tells the user which number to call instead");
        assertFalse(fake.reasonsHi().get(0).isBlank());

        var landline = care.check("011-41234567", "HDFC Bank");
        assertEquals("SUSPICIOUS", landline.verdict());
        assertTrue(landline.reasonCodes().contains("NOT_IN_OFFICIAL_LIST"));

        // ICICI's real number claimed to be SBI's is not SBI's
        var other = care.check("1800 1080", "SBI");
        assertFalse(other.official());
        assertEquals("SUSPICIOUS", other.verdict());
    }

    @Test
    void brandWithoutDirectoryAndNoBrand() {
        var amazon = care.check("9123456789", "Amazon");
        assertEquals("SUSPICIOUS", amazon.verdict(), "a mobile as Amazon's helpline; we have no verified Amazon numbers");
        assertTrue(amazon.reasonCodes().contains("NO_DIRECTORY"));
        assertNotNull(amazon.officialUrl());
        assertEquals("UNKNOWN", care.check("9123456789", null).verdict());
        assertEquals("UNKNOWN", care.check("040-23456789", null).verdict());
        assertEquals("UNKNOWN", care.check("040-23456789", "no such brand").verdict());
    }

    @Test
    void communityReports() {
        reportedNumbers.add("4023456789");
        var r = care.check("+91 40 2345 6789", null);
        assertEquals("FAKE", r.verdict());
        assertTrue(r.reasonCodes().contains("COMMUNITY_REPORTED"));
        assertEquals("FAKE", care.check("04023456789", "HDFC").verdict());
    }

    @Test
    void numberStoreIsReadByHashPrefixOnly() {
        var numbers = mock(com.fraudshield.calls.NumberReputationService.class);
        String h = com.fraudshield.calls.PhoneNumbers.hash("+914023456789");
        when(numbers.range(h.substring(0, 5))).thenReturn(new com.fraudshield.calls.NumberReputationService.Range(h.substring(0, 5),
                java.util.List.of(new com.fraudshield.calls.NumberReputationService.RangeEntry(h.substring(5), "SCAM", 6, 6.0, "SUSPICIOUS", null))));
        when(numbers.range(argThat(p -> !p.equals(h.substring(0, 5))))).thenReturn(
                new com.fraudshield.calls.NumberReputationService.Range("x", java.util.List.of()));
        assertTrue(CareNumberService.isReported(numbers, "040-2345 6789"));
        assertFalse(CareNumberService.isReported(numbers, "040-2345 6780"));
        assertFalse(CareNumberService.isReported(numbers, "1930"), "short codes are not in the number store");
        verify(numbers, never()).range(h); // never the full hash
    }

    @Test
    void brandMatching() {
        assertEquals("State Bank of India", care.brand("sbi").orElseThrow().name());
        assertEquals("State Bank of India", care.brand("STATE BANK OF INDIA").orElseThrow().name());
        assertEquals("HDFC Bank", care.brand("hdfc").orElseThrow().name());
        assertTrue(care.brand("").isEmpty());
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> care.check("12", null));
    }
}
