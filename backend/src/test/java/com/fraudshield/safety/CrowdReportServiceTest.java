package com.fraudshield.safety;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fraudshield.domain.User;
import com.fraudshield.repo.Repositories.UserRepository;
import com.fraudshield.safety.CrowdItem.Status;
import com.fraudshield.safety.CrowdItem.Type;
import com.fraudshield.safety.SafetyRepositories.CrowdItemRepository;
import com.fraudshield.safety.SafetyRepositories.CrowdReportRepository;
import com.fraudshield.service.BlocklistService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/** 15.7: de-duplication, trust weights, the SUSPICIOUS threshold, the daily cap and the moderator's decisions. */
class CrowdReportServiceTest {
    private final CrowdItemRepository items = mock(CrowdItemRepository.class);
    private final CrowdReportRepository reports = mock(CrowdReportRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final BlocklistService blocklist = mock(BlocklistService.class);
    private final Instant now = Instant.parse("2026-11-24T10:00:00Z");
    private final Map<Long, CrowdItem> itemStore = new HashMap<>();
    private final List<CrowdReport> votes = new ArrayList<>();
    private CrowdReportService crowd;

    @BeforeEach
    void inMemory() {
        when(items.findByTypeAndValueHash(any(), any())).thenAnswer(i -> itemStore.values().stream()
                .filter(x -> x.getType() == i.getArgument(0) && x.getValueHash().equals(i.getArgument(1))).findFirst());
        when(items.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(itemStore.get((Long) i.getArgument(0))));
        when(items.save(any())).thenAnswer(i -> i.getArgument(0));
        when(items.saveAndFlush(any())).thenAnswer(i -> {
            CrowdItem c = i.getArgument(0);
            ReflectionTestUtils.setField(c, "id", (long) itemStore.size() + 1);
            itemStore.put(c.getId(), c);
            return c;
        });
        when(reports.saveAndFlush(any())).thenAnswer(i -> { votes.add(i.getArgument(0)); return i.getArgument(0); });
        when(reports.existsByItemIdAndReporterId(anyLong(), anyLong())).thenAnswer(i -> votes.stream()
                .anyMatch(v -> v.getItemId().equals(i.getArgument(0)) && v.getReporterId().equals(i.getArgument(1))));
        when(reports.findByItemId(anyLong())).thenAnswer(i -> votes.stream().filter(v -> v.getItemId().equals(i.getArgument(0))).toList());
        when(reports.countByReporterIdAndCreatedAtAfter(anyLong(), any())).thenAnswer(i -> votes.stream()
                .filter(v -> v.getReporterId().equals(i.getArgument(0))).count());
        crowd = new CrowdReportService(items, reports, users, blocklist, 5.0, 0.2, 0.5, Duration.ofDays(7), 30, 3,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private void user(long id, Duration age) {
        User u = new User(id + "@t.com", "x");
        ReflectionTestUtils.setField(u, "id", id);
        ReflectionTestUtils.setField(u, "createdAt", now.minus(age));
        when(users.findById(id)).thenReturn(Optional.of(u));
    }

    @Test
    void fiveTrustedUsersMakeItSuspicious() {
        for (long u = 1; u <= 5; u++) {
            user(u, Duration.ofDays(60));
            var o = crowd.report(u, Type.UPI, "scam.desk@ybl", null);
            assertTrue(o.counted());
            assertEquals(u < 5 ? Status.UNVERIFIED : Status.SUSPICIOUS, o.status(), "after " + u);
        }
    }

    @Test
    void oneUserTwentyTimesCountsOnce() {
        user(1, Duration.ofDays(60));
        for (int i = 0; i < 20; i++) assertEquals(i == 0, crowd.report(1L, Type.UPI, "scam.desk@ybl", null).counted());
        assertEquals(1, itemStore.values().iterator().next().getReportCount());
        assertEquals(Status.UNVERIFIED, itemStore.values().iterator().next().getStatus());
    }

    @Test
    void newAccountsWeighLittle() {
        for (long u = 1; u <= 20; u++) { user(u, Duration.ofHours(2)); crowd.report(u, Type.LINK, "https://bad.top/x", null); }
        CrowdItem i = itemStore.values().iterator().next();
        assertEquals(20, i.getReportCount());
        assertEquals(4.0, i.getTrustScore(), 1e-9);
        assertEquals(Status.UNVERIFIED, i.getStatus());
        assertEquals("bad.top", i.getLinkHost(), "a website is kept by host so it can be blocked");
    }

    @Test
    void falseReportersLoseTrust() {
        user(1, Duration.ofDays(60));
        when(reports.countByReporterAndItemStatus(1L, Status.CLEARED)).thenReturn(1L);
        assertEquals(0.5, crowd.weightOf(1L), 1e-9);
        when(reports.countByReporterAndItemStatus(1L, Status.CLEARED)).thenReturn(3L);
        assertEquals(0.0, crowd.weightOf(1L), 1e-9);
    }

    @Test
    void dailyCap() {
        user(1, Duration.ofDays(60));
        for (int i = 0; i < 30; i++) crowd.report(1L, Type.UPI, "id" + i + "@ybl", null);
        var e = assertThrows(ResponseStatusException.class, () -> crowd.report(1L, Type.UPI, "one.more@ybl", null));
        assertEquals(429, e.getStatusCode().value());
    }

    @Test
    void hashOnlyReportsMatchValueReports_andNoRawUpiIsKept() {
        user(1, Duration.ofDays(60));
        user(2, Duration.ofDays(60));
        crowd.report(1L, Type.UPI, "Scam.Desk@YBL", null);
        crowd.report(2L, Type.UPI, null, SafetyValues.hash(Type.UPI, "scam.desk@ybl"));
        assertEquals(1, itemStore.size());
        CrowdItem i = itemStore.values().iterator().next();
        assertEquals(2, i.getReportCount());
        assertNull(i.getLinkHost());
        assertThrows(ResponseStatusException.class, () -> crowd.report(1L, Type.UPI, null, "NOTAHASH"));
    }

    @Test
    void moderatorDecisions() {
        user(1, Duration.ofDays(60));
        crowd.report(1L, Type.LINK, "http://www.fake-sbi.top/", null);
        long id = itemStore.keySet().iterator().next();
        assertEquals(Status.CONFIRMED, crowd.review(id, CrowdReportService.Decision.CONFIRM).status());
        verify(blocklist).addDomainIfMissing(eq("fake-sbi.top"), anyString());

        crowd.report(1L, Type.UPI, "innocent@okaxis", null);
        long id2 = itemStore.keySet().stream().max(Long::compare).orElseThrow();
        assertEquals(Status.CLEARED, crowd.review(id2, CrowdReportService.Decision.CLEAR).status());
        user(2, Duration.ofDays(60));
        crowd.report(2L, Type.UPI, "innocent@okaxis", null);
        assertEquals(Status.CLEARED, itemStore.get(id2).getStatus(), "reviewed items keep the moderator's decision");
        verifyNoMoreInteractions(blocklist);
    }

    @Test
    void queueShowsSuspiciousAndBusyItems() {
        CrowdItem a = new CrowdItem(Type.UPI, "a".repeat(64), null);
        a.tally(5, 5.0, Status.SUSPICIOUS);
        CrowdItem b = new CrowdItem(Type.UPI, "b".repeat(64), null);
        b.tally(1, 1.0, Status.UNVERIFIED);
        CrowdItem c = new CrowdItem(Type.UPI, "c".repeat(64), null);
        c.tally(4, 0.8, Status.UNVERIFIED);
        when(items.findByStatusInAndReviewedAtIsNullOrderByTrustScoreDesc(any())).thenReturn(List.of(a, b, c));
        assertEquals(List.of("a".repeat(64), "c".repeat(64)), crowd.queue().stream().map(CrowdReportService.ItemView::valueHash).toList());
    }
}
