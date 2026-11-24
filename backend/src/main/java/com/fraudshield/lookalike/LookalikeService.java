package com.fraudshield.lookalike;

import com.fraudshield.domain.BrandEntry;
import com.fraudshield.lookalike.BrandRegistry.Brand;
import com.fraudshield.lookalike.LookalikeMatcher.Match;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 10.1-10.4 as one bean. Starts with {@code seed/brands.csv}; {@link BrandSeeder} switches it to the brands table
 * once the table is filled, and {@link #reload} does so after admin changes. Lookups never touch the database.
 */
@Service
public class LookalikeService {
    /** The public-suffix list and the popular list are big and never change at run time: load them once per JVM. */
    private static final class Shared {
        static final DomainNormalizer NORMALIZER = DomainNormalizer.fromClasspath();
        static final PopularDomains POPULAR = PopularDomains.fromClasspath();
    }

    private volatile BrandRegistry registry;
    private volatile LookalikeMatcher matcher;

    public LookalikeService() { use(BrandRegistry.seedFromClasspath()); }

    /** Plain-Java users (unit tests, old constructors) share one instance. */
    public static LookalikeService defaults() { return Holder.INSTANCE; }
    private static final class Holder { static final LookalikeService INSTANCE = new LookalikeService(); }

    public DomainNormalizer normalizer() { return Shared.NORMALIZER; }
    public NormalizedDomain normalize(String url) { return Shared.NORMALIZER.normalize(url); }
    public boolean isPopular(String registrable) { return Shared.POPULAR.contains(registrable); }
    public BrandRegistry registry() { return registry; }

    public Optional<Match> check(NormalizedDomain d, String path) { return matcher.check(d, path); }

    /** Replaces the brand list (from the brands table). An empty list is ignored - never run without brands. */
    public void reload(List<BrandEntry> rows) {
        if (rows == null || rows.isEmpty()) return;
        var favicons = new java.util.HashMap<String, String>();
        rows.forEach(r -> { if (r.getFaviconHash() != null && !r.getFaviconHash().isBlank()) favicons.put(r.getName(), r.getFaviconHash().strip()); });
        use(rows.stream().map(r -> new Brand(r.getName(), r.getCategory(), BrandRegistry.split(r.getOfficialDomains()),
                BrandRegistry.split(r.getKeywords()))).filter(b -> !b.officialHosts().isEmpty()).toList(), favicons);
    }

    private void use(List<Brand> brands) { use(brands, java.util.Map.of()); }

    private synchronized void use(List<Brand> brands, java.util.Map<String, String> favicons) {
        var r = new BrandRegistry(brands, Shared.NORMALIZER, favicons);
        var m = new LookalikeMatcher(r, Shared.POPULAR);
        registry = r; matcher = m;
    }
}
