package com.fraudshield.service;

/** External URL reputation source (Google Safe Browsing, later VirusTotal). */
public interface ReputationProvider {
    /**
     * @return extra risk score (0-100) for a listed URL, 0 when not listed, or {@code null} when the provider
     *         could not answer (error/timeout). {@code null} is never cached so the next scan retries.
     */
    Integer score(String url);
}
