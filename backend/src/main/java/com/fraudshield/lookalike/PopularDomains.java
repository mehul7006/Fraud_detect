package com.fraudshield.lookalike;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 10.4: well-known sites (Tranco top 10k) that are never reported as brand look-alikes, so {@code flickr.com},
 * {@code flipboard.com} or {@code amazonaws.com} are not "fake Flipkart / Amazon". A phishing domain does not reach
 * the top 10k of a list averaged over months of real traffic.
 */
public final class PopularDomains {
    private final Set<String> domains;

    public PopularDomains(Set<String> domains) { this.domains = Set.copyOf(domains); }

    public boolean contains(String registrable) { return registrable != null && domains.contains(registrable); }

    public int size() { return domains.size(); }

    public static PopularDomains fromClasspath() {
        try (InputStream in = PopularDomains.class.getResourceAsStream("/seed/popular-domains.txt")) {
            if (in == null) throw new IllegalStateException("seed/popular-domains.txt missing");
            Set<String> s = new HashSet<>();
            for (String l : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\r?\\n")) {
                l = l.strip().toLowerCase(Locale.ROOT);
                if (!l.isEmpty() && !l.startsWith("#")) s.add(l);
            }
            return new PopularDomains(s);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
