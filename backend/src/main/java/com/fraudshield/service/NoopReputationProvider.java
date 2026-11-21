package com.fraudshield.service;

public class NoopReputationProvider implements ReputationProvider {
    @Override public Integer score(String url) { return 0; }
}
