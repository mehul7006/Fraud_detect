package com.fraudshield.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "carrier_patterns")
public class CarrierPattern {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String country;
    private String carrier;
    private String kind;
    private String regex;
    private int priority;
    private boolean enabled;
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected CarrierPattern() {}

    public Long getId() { return id; }
    public String getCountry() { return country; }
    public String getCarrier() { return carrier; }
    public String getKind() { return kind; }
    public String getRegex() { return regex; }
    public int getPriority() { return priority; }
    public boolean isEnabled() { return enabled; }
}
