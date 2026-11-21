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
    public Blocklist(String kind, String value, String reason) { this.kind = kind; this.value = value; this.reason = reason; }
    public Long getId() { return id; }
    public String getKind() { return kind; }
    public String getValue() { return value; }
    public String getReason() { return reason; }
}
