package com.fraudshield.service;

import com.fraudshield.domain.Device;
import com.fraudshield.domain.Enums.Severity;
import com.fraudshield.domain.Enums.ThreatType;
import com.fraudshield.domain.ThreatEvent;
import com.fraudshield.repo.Repositories.DeviceRepository;
import com.fraudshield.repo.Repositories.ThreatEventRepository;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 7.2: the 10-minute correlation window. */
class TakeoverServiceTest {
    private final DeviceRepository devices = mock(DeviceRepository.class);
    private final ThreatEventRepository events = mock(ThreatEventRepository.class);
    private final ThreatService threats = mock(ThreatService.class);

    private TakeoverService at(Instant now) {
        return new TakeoverService(devices, events, threats, Duration.ofMinutes(10), Clock.fixed(now, ZoneOffset.UTC));
    }

    private void twoDevicesAndAnOtp() throws Exception {
        Device first = new Device(1L, "mine");
        Thread.sleep(5); // the second phone appears after the first
        Device second = new Device(1L, "attacker");
        when(devices.findByUserId(1L)).thenReturn(List.of(first, second));
        when(events.findByUserIdAndTypeInAndCreatedAtAfter(eq(1L), anyCollection(), any()))
                .thenAnswer(inv -> {
                    Instant since = inv.getArgument(2);
                    ThreatEvent otp = new ThreatEvent(1L, ThreatType.UNEXPECTED_OTP, Severity.HIGH, "OTP", "", "app");
                    return otp.getCreatedAt().isAfter(since) ? List.of(otp) : List.of();
                });
        when(threats.report(anyLong(), any(), any(), any(), any(), any()))
                .thenReturn(new ThreatService.Reported(null, true));
    }

    @Test
    void bothSignalsInsideTheWindow_raiseCriticalTakeover() throws Exception {
        twoDevicesAndAnOtp();
        assertTrue(at(Instant.now()).check(1L).isPresent());
        verify(threats).report(eq(1L), eq(ThreatType.ACCOUNT_TAKEOVER), eq(Severity.CRITICAL), anyString(),
                contains("New device signed in"), eq("correlation"));
    }

    @Test
    void sameSignalsElevenMinutesLater_doNothing() throws Exception {
        twoDevicesAndAnOtp();
        assertTrue(at(Instant.now().plus(Duration.ofMinutes(11))).check(1L).isEmpty());
        verify(threats, never()).report(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void theAccountsFirstPhoneIsNeverANewDevice() {
        when(devices.findByUserId(1L)).thenReturn(List.of(new Device(1L, "only")));
        assertTrue(at(Instant.now()).deviceSignals(1L, Instant.now().minus(Duration.ofMinutes(10))).isEmpty());
    }
}
