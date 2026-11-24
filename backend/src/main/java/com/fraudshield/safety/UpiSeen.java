package com.fraudshield.safety;

import jakarta.persistence.*;
import java.time.Instant;

/** 15.5: since when FraudShield has known a (hashed) UPI ID. Not linked to any user. */
@Entity
@Table(name = "upi_seen")
public class UpiSeen {
    @Id @Column(name = "vpa_hash", length = 64)
    private String vpaHash;
    @Column(name = "first_seen", nullable = false)
    private Instant firstSeen;
    @Column(nullable = false)
    private int checks = 1;

    protected UpiSeen() {}
    public UpiSeen(String vpaHash, Instant firstSeen) { this.vpaHash = vpaHash; this.firstSeen = firstSeen; }

    public String getVpaHash() { return vpaHash; }
    public Instant getFirstSeen() { return firstSeen; }
    public int getChecks() { return checks; }
    void seenAgain() { checks++; }
}
