package com.fraudshield.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;

/**
 * Cache decision (4.2.1): the key is the URL without query/fragment. A host-only key would hide a bad path on a good
 * host, and the query often carries personal data. Provider failures (null) are never cached.
 */
public class CachingReputationProvider implements ReputationProvider {
    private final ReputationProvider delegate;
    private final Cache<String, Integer> cache;

    public CachingReputationProvider(ReputationProvider delegate, long maxEntries, Duration ttl) {
        this.delegate = delegate;
        this.cache = Caffeine.newBuilder().maximumSize(maxEntries).expireAfterWrite(ttl).recordStats().build();
    }

    @Override
    public Integer score(String url) {
        String key = key(url);
        Integer hit = cache.getIfPresent(key);
        if (hit != null) return hit;
        Integer fresh = delegate.score(url);
        if (fresh != null) cache.put(key, fresh);
        return fresh;
    }

    public com.github.benmanes.caffeine.cache.stats.CacheStats stats() { return cache.stats(); }

    static String key(String url) {
        int cut = url.length();
        int q = url.indexOf('?');
        int h = url.indexOf('#');
        if (q >= 0) cut = Math.min(cut, q);
        if (h >= 0) cut = Math.min(cut, h);
        return url.substring(0, cut).toLowerCase();
    }
}
