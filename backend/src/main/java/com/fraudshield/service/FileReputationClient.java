package com.fraudshield.service;

import java.util.Optional;

/** External file reputation by SHA-256 hash. Only the hash is ever sent, never the file (roadmap 5.1). */
public interface FileReputationClient {
    /** malicious = engines that flagged the file, total = engines that answered (0 total = hash unknown). */
    record Result(int malicious, int total) {}

    /** Empty when the service is off, rate limited, or the call failed. */
    Optional<Result> lookup(String sha256);
}
