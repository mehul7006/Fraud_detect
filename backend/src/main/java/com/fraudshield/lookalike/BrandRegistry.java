package com.fraudshield.lookalike;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 10.1: in-memory index of the brands table (or of {@code seed/brands.csv} in unit tests).
 * Lookup by registrable domain answers "is this an official site, and of which brand?".
 * Immutable; the Spring bean swaps in a new instance on reload.
 */
public final class BrandRegistry {

    /** One brand. {@code officialHosts} are as listed (may be sub-domains like pay.google.com). */
    public record Brand(String name, String category, List<String> officialHosts, List<String> keywords) {}

    private final List<Brand> brands;
    private final Map<String, Brand> byRegistrable = new HashMap<>();
    private final Map<String, String> urlByName = new HashMap<>();
    private final Map<String, Set<String>> labelsByName = new HashMap<>();
    private final Map<String, String> faviconByName;

    public BrandRegistry(List<Brand> brands, DomainNormalizer norm) { this(brands, norm, Map.of()); }

    /** @param favicons brand name -> SHA-256 (hex) of the real site's favicon (10.8), may be empty */
    public BrandRegistry(List<Brand> brands, DomainNormalizer norm, Map<String, String> favicons) {
        this.brands = List.copyOf(brands);
        this.faviconByName = Map.copyOf(favicons);
        for (Brand b : brands) {
            for (String h : b.officialHosts()) {
                var n = norm.normalize(h);
                if (n != null) {
                    byRegistrable.putIfAbsent(n.registrable(), b);
                    labelsByName.computeIfAbsent(b.name(), k -> new HashSet<>()).add(n.label().replaceAll("[^a-z0-9]", ""));
                }
            }
            String first = b.officialHosts().get(0);
            var n = norm.normalize(first);
            urlByName.put(b.name(), "https://" + (n != null && n.subdomain().isEmpty() ? "www." + first : first));
        }
    }

    /** 10.8: SHA-256 of the brand's real favicon, or null when not known. */
    public String faviconHash(Brand b) { return faviconByName.get(b.name()); }

    public boolean hasFavicons() { return !faviconByName.isEmpty(); }

    /** Where "Go to the real site" leads, e.g. https://www.flipkart.com, https://pay.google.com. */
    public String officialUrl(Brand b) { return urlByName.get(b.name()); }

    public List<Brand> brands() { return brands; }

    /** Names of the brand's real domains without ending: "flipkart", "shopsy", "onlinesbi"... */
    public Set<String> officialLabels(Brand b) { return labelsByName.getOrDefault(b.name(), Set.of()); }

    /** The brand whose official domain this is (e.g. "flipkart.com", "sbi.co.in"), if any. */
    public Optional<Brand> officialBrand(String registrable) { return Optional.ofNullable(byRegistrable.get(registrable)); }

    public int size() { return brands.size(); }

    /** CSV: name,category,official_domains,keywords (lists separated by '|'). */
    public static List<Brand> parseCsv(String csv) {
        List<Brand> out = new ArrayList<>();
        String[] lines = csv.split("\\r?\\n");
        for (int i = 1; i < lines.length; i++) {
            String l = lines[i].strip();
            if (l.isEmpty() || l.startsWith("#")) continue;
            String[] c = l.split(",", -1);
            if (c.length < 4) throw new IllegalArgumentException("brands.csv line " + (i + 1) + ": 4 columns expected");
            var hosts = split(c[2]);
            if (hosts.isEmpty()) throw new IllegalArgumentException("brands.csv line " + (i + 1) + ": no official domain");
            out.add(new Brand(c[0].strip(), c[1].strip(), hosts, split(c[3])));
        }
        return out;
    }

    public static List<Brand> seedFromClasspath() {
        try (InputStream in = BrandRegistry.class.getResourceAsStream("/seed/brands.csv")) {
            if (in == null) throw new IllegalStateException("seed/brands.csv missing");
            return parseCsv(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<String> split(String s) {
        return Arrays.stream(s.split("\\|")).map(String::strip).map(x -> x.toLowerCase(Locale.ROOT))
                .filter(x -> !x.isEmpty()).distinct().toList();
    }
}
