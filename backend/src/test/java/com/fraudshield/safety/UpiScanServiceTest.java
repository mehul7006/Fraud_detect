package com.fraudshield.safety;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fraudshield.lookalike.LookalikeService;
import com.fraudshield.safety.CrowdItem.Status;
import com.fraudshield.safety.SafetyRepositories.UpiSeenRepository;
import com.fraudshield.safety.UpiScanService.Kind;
import com.fraudshield.service.UrlScanService;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 15.5: reported UPI ID flagged, collect-request QR flagged, ordinary payments to people / shops stay SAFE. */
class UpiScanServiceTest {
    private final CrowdReportService crowd = mock(CrowdReportService.class);
    private final UpiSeenRepository seen = mock(UpiSeenRepository.class);
    private final UrlScanService urls = mock(UrlScanService.class);
    private final Instant now = Instant.parse("2026-11-24T10:00:00Z");
    private UpiScanService upi;

    @BeforeEach
    void setUp() {
        when(crowd.status(any(), any())).thenReturn(Status.UNVERIFIED);
        // known for a month unless a test says otherwise
        when(seen.findById(any())).thenAnswer(i -> Optional.of(new UpiSeen(i.getArgument(0), now.minus(Duration.ofDays(30)))));
        upi = new UpiScanService(LookalikeService.defaults(), crowd, seen, urls, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void ordinaryPersonAndShopAreSafe() {
        assertEquals("SAFE", upi.scanVpa("ramesh.kumar@okaxis").verdict());
        assertEquals("SAFE", upi.scanVpa("9876543210@ybl").verdict());
        var shop = upi.scanQr("upi://pay?pa=sharmagrocery@ybl&pn=Sharma%20Grocery&am=240&cu=INR");
        assertEquals("SAFE", shop.verdict());
        assertEquals(Kind.PAY, shop.kind());
        assertEquals("240", shop.payee().amount());
        assertEquals("PhonePe", shop.payee().handleOwner());
        // a surname that is also a brand is not a brand claim
        assertEquals("SAFE", upi.scanQr("upi://pay?pa=rkotak@okicici&pn=Ramesh%20Kotak").verdict());
    }

    @Test
    void reportedVpaIsFlagged() {
        String h = SafetyValues.hash(CrowdItem.Type.UPI, "quickloan.help@ybl");
        when(crowd.status(CrowdItem.Type.UPI, h)).thenReturn(Status.SUSPICIOUS);
        var r = upi.scanVpa("QuickLoan.Help@YBL");
        assertEquals("MALICIOUS", r.verdict());
        assertTrue(r.reasonCodes().contains("COMMUNITY_REPORTED"));
        when(crowd.status(CrowdItem.Type.UPI, h)).thenReturn(Status.CONFIRMED);
        assertEquals(100, upi.scanVpa("quickloan.help@ybl").score());
    }

    @Test
    void collectRequestQrIsFlagged() {
        var r = upi.scanQr("upi://collect?pa=refund.desk@ybl&pn=Refund&am=4999&tn=Receive%20your%20refund");
        assertEquals(Kind.COLLECT, r.kind());
        assertEquals("MALICIOUS", r.verdict());
        assertTrue(r.reasonCodes().contains("COLLECT_REQUEST"));
        assertFalse(r.reasonsHi().isEmpty());
    }

    @Test
    void scanToReceiveMoneyTrickIsFlagged() {
        var r = upi.scanQr("upi://pay?pa=9123456780@ybl&pn=Prize%20Dept&am=1999&tn=Scan%20to%20receive%20your%20prize%20money");
        assertTrue(r.reasonCodes().contains("RECEIVE_MONEY_TRICK"), r.reasonCodes().toString());
        assertEquals("MALICIOUS", r.verdict());
    }

    @Test
    void brandNameOnAPersonalIdIsFlagged() {
        var r = upi.scanQr("upi://pay?pa=9812345678@ybl&pn=SBI%20Customer%20Care&am=10");
        assertEquals("State Bank of India", r.claimedBrand());
        assertTrue(r.reasonCodes().contains("BRAND_NAME_MISMATCH"));
        assertNotEquals("SAFE", r.verdict());
        assertTrue(r.reasons().stream().anyMatch(s -> s.contains("State Bank of India")));
        var id = upi.scanVpa("amazon.refund@okhdfcbank");
        assertEquals("Amazon", id.claimedBrand());
        assertNotEquals("SAFE", id.verdict());
        // Amazon's own handle claiming Amazon is not a mismatch
        assertFalse(upi.scanVpa("amazon@apl").reasonCodes().contains("BRAND_NAME_MISMATCH"));
    }

    @Test
    void invalidInputsAndUnknownHandles() {
        assertEquals("INVALID_VPA", upi.scanVpa("not a upi id").reasonCodes().get(0));
        assertEquals(Kind.INVALID, upi.scanQr("hello world").kind());
        assertEquals(Kind.INVALID, upi.scanQr("upi://pay?pn=NoPayee").kind());
        var odd = upi.scanVpa("someone@weirdbank");
        assertTrue(odd.reasonCodes().contains("UNKNOWN_HANDLE"));
        assertFalse(odd.payee().handleKnown());
        assertEquals("SAFE", odd.verdict(), "an unknown handle alone is only a hint (our list is not complete)");
    }

    @Test
    void newVpaOnlyAddsToOtherSigns() {
        when(seen.findById(any())).thenReturn(Optional.empty());
        var plain = upi.scanVpa("ravi.s@okaxis");
        assertEquals("SAFE", plain.verdict());
        assertFalse(plain.reasonCodes().contains("NEW_VPA"));
        var odd = upi.scanVpa("kyc.update@ybl");
        assertTrue(odd.reasonCodes().contains("NEW_VPA"), odd.reasonCodes().toString());
        verify(seen, atLeastOnce()).saveAndFlush(any());
    }

    @Test
    void mandateIsCalledOut() {
        var r = upi.scanQr("upi://mandate?pa=ott.sub@ybl&pn=Subscription&am=99");
        assertEquals(Kind.MANDATE, r.kind());
        assertTrue(r.reasonCodes().contains("AUTOPAY_MANDATE"));
    }

    @Test
    void queryDecoding() {
        var q = UpiScanService.query("upi://pay?pa=a.b@ybl&pn=A%20B&am=10.50&tn=x+y&bad=%zz");
        assertEquals("A B", q.get("pn"));
        assertEquals("10.50", q.get("am"));
        assertEquals("%zz", q.get("bad"));
        assertNull(UpiScanService.amount("-5"));
    }
}
