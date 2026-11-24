package com.fraudshield.safety;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fraudshield.domain.Enums.Severity;
import com.fraudshield.domain.Enums.ThreatType;
import com.fraudshield.dto.Dtos.ThreatDto;
import com.fraudshield.safety.SafetyRepositories.ScamSignalRepository;
import com.fraudshield.safety.ScamSessionService.Level;
import com.fraudshield.safety.ScamSignal.Kind;
import com.fraudshield.service.ThreatService;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 15.1: four live-scam signals inside 10 minutes -> one CRITICAL; fewer, or spread out, stay below it. */
class ScamSessionServiceTest {
    private final ScamSignalRepository repo = mock(ScamSignalRepository.class);
    private final ThreatService threats = mock(ThreatService.class);
    private final List<ScamSignal> stored = new ArrayList<>();
    private final Instant t0 = Instant.parse("2026-11-24T10:00:00Z");

    @BeforeEach
    void fakeRepo() {
        when(repo.save(any())).thenAnswer(i -> { stored.add(i.getArgument(0)); return i.getArgument(0); });
        when(repo.findByUserIdAndCreatedAtAfter(anyLong(), any())).thenAnswer(i -> stored.stream()
                .filter(s -> s.getUserId().equals(i.getArgument(0)) && s.getCreatedAt().isAfter(i.getArgument(1))).toList());
        when(threats.report(anyLong(), any(), any(), any(), any(), any())).thenReturn(new ThreatService.Reported(
                new ThreatDto(42L, "LIVE_SCAM", "CRITICAL", "Possible scam in progress", "", "correlation", "OPEN", "", null, null), true));
    }

    private ScamSessionService at(Instant now) {
        return new ScamSessionService(repo, threats, Duration.ofMinutes(10), 180, List.of("com.anydesk.anydeskandroid"),
                Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void fourSignalsInTenMinutes_raiseOneCritical() {
        at(t0).signal(1L, Kind.LONG_UNKNOWN_CALL, true, 240);
        at(t0.plusSeconds(60)).signal(1L, Kind.REMOTE_ACCESS_APP, null, null);
        at(t0.plusSeconds(180)).signal(1L, Kind.BANKING_APP, null, null);
        var r = at(t0.plusSeconds(400)).signal(1L, Kind.OTP_SEEN, null, null);
        assertEquals(Level.CRITICAL, r.level());
        assertEquals(42L, r.threatId());
        assertEquals(4, r.signals().size());
        assertNotNull(r.advice().get("hi"));
        verify(threats, times(1)).report(eq(1L), eq(ThreatType.LIVE_SCAM), eq(Severity.CRITICAL), eq("Possible scam in progress"),
                contains("screen-sharing"), eq("correlation"));
    }

    @Test
    void twoSignalsStayBelow() {
        at(t0).signal(1L, Kind.BANKING_APP, null, null);
        var r = at(t0.plusSeconds(30)).signal(1L, Kind.OTP_SEEN, null, null);
        assertEquals(Level.NONE, r.level());
        assertNull(r.threatId());
        verifyNoInteractions(threats);
    }

    @Test
    void callPlusScreenShareApp_plusOneMore_isAWarningOnly() {
        at(t0).signal(1L, Kind.LONG_UNKNOWN_CALL, true, 600);
        at(t0.plusSeconds(10)).signal(1L, Kind.REMOTE_ACCESS_APP, null, null);
        var r = at(t0.plusSeconds(20)).signal(1L, Kind.BANKING_APP, null, null);
        assertEquals(Level.WARNING, r.level());
        verifyNoInteractions(threats);
    }

    @Test
    void shortCallsAndCallsFromContactsDoNotCount() {
        assertFalse(at(t0).signal(1L, Kind.LONG_UNKNOWN_CALL, true, 60).counted(), "1-minute call is not 'long'");
        assertFalse(at(t0).signal(1L, Kind.LONG_UNKNOWN_CALL, false, 900).counted(), "a saved contact is not unknown");
        assertFalse(at(t0).signal(1L, Kind.LONG_UNKNOWN_CALL, true, null).counted());
        assertTrue(stored.isEmpty());
    }

    @Test
    void signalsSpreadOverMoreThanTheWindow_doNothing() {
        at(t0).signal(1L, Kind.LONG_UNKNOWN_CALL, true, 240);
        at(t0.plusSeconds(60)).signal(1L, Kind.REMOTE_ACCESS_APP, null, null);
        at(t0.plusSeconds(120)).signal(1L, Kind.BANKING_APP, null, null);
        var r = at(t0.plus(Duration.ofMinutes(11))).signal(1L, Kind.OTP_SEEN, null, null);
        assertNotEquals(Level.CRITICAL, r.level());
        verifyNoInteractions(threats);
    }

    @Test
    void anotherUsersSignalsAreNotMixedIn() {
        at(t0).signal(1L, Kind.LONG_UNKNOWN_CALL, true, 240);
        at(t0).signal(1L, Kind.REMOTE_ACCESS_APP, null, null);
        at(t0).signal(2L, Kind.BANKING_APP, null, null);
        var r = at(t0.plusSeconds(5)).signal(2L, Kind.OTP_SEEN, null, null);
        assertEquals(Level.NONE, r.level());
        verifyNoInteractions(threats);
    }

    @Test
    void oldSignalsArePurged() {
        at(t0).signal(1L, Kind.OTP_SEEN, null, null);
        verify(repo).deleteOlderThan(t0.minus(Duration.ofHours(24)));
    }
}
