package com.fraudshield.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "blocklist")
public class Blocklist {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String kind;
    private String value;
    private String reason;

    protected Blocklist() {}
    public String getKind() { return kind; }
    public String getValue() { return value; }
    public String getReason() { return reason; }
}
