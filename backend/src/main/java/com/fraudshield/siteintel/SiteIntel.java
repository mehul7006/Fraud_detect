package com.fraudshield.siteintel;

import com.fraudshield.lookalike.BrandRegistry.Brand;
import com.fraudshield.lookalike.LookalikeService;
import com.fraudshield.lookalike.NormalizedDomain;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 10.5-10.8 under one time budget (10.10). Domain facts (RDAP) and the page (fetch + certificate + redirects) are
 * looked up in parallel; whatever is not ready at the deadline is left out ("degrades"), the scan still answers.
 * Unfinished lookups keep running and fill the 24 h per-host cache, so the next scan of that host is instant.
 * Official brand sites and popular sites are not looked at at all.
 */
public final class SiteIntel {
    private static final Logger log = LoggerFactory.getLogger(SiteIntel.class);

    /** {@code brand}: brand the site pretends to be (found on the page / at the end of the redirects), or null. */
    public record Facts(List<Finding> findings, Brand brand, String finalUrl, boolean complete) {
        static final Facts NONE = new Facts(List.of(), null, null, true);
    }

    private final LookalikeService lookalike;
    private final DomainIntelService rdap;
    private final PageFetcher pages;
    private final SafeHttp http;
    private final TlsRedirectService tls;
    private final Duration budget;
    private final ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
    private final Cache<String, Facts> cache = Caffeine.newBuilder().maximumSize(10_000).expireAfterWrite(Duration.ofHours(24)).build();

    public SiteIntel(LookalikeService lookalike, DomainIntelService rdap, SafeHttp http, Duration budget) {
        this.lookalike = lookalike; this.rdap = rdap; this.http = http; this.pages = new PageFetcher(http);
        this.tls = new TlsRedirectService(lookalike); this.budget = budget;
    }

    public ExecutorService executor() { return pool; }

    /** Called by Spring on shutdown. */
    public void close() { pool.shutdownNow(); }

    /** True when there is nothing to learn (official or very popular site). */
    public boolean skip(NormalizedDomain d) {
        if (d != null && com.fraudshield.lookalike.LookalikeMatcher.SHORTENERS.contains(d.registrable())) return false; // follow short links
        return d == null || lookalike.registry().officialBrand(d.registrable()).isPresent() || lookalike.isPopular(d.registrable())
                || com.fraudshield.lookalike.LookalikeMatcher.cdnZone(d);
    }

    /** @param claimed brand already named by the address check (may be null); @param deadline hard end of the scan */
    public Facts examine(NormalizedDomain d, String url, Brand claimed, Instant deadline) {
        if (skip(d)) return Facts.NONE;
        Facts hit = cache.getIfPresent(d.host());
        if (hit != null) return hit;
        Instant end = min(deadline, Instant.now().plus(budget));
        var dom = d.ip() ? CompletableFuture.completedFuture(DomainIntelService.DomainFacts.unknown(d.host()))
                : CompletableFuture.supplyAsync(() -> rdap.lookup(d.registrable()), pool);
        var page = CompletableFuture.supplyAsync(() -> pages.fetch(url, end.minusMillis(50)), pool);

        Facts f = assemble(d, claimed, now(dom, end), now(page, end), end);
        if (f.complete()) cache.put(d.host(), f);
        else CompletableFuture.allOf(dom, page).thenRunAsync(() -> {   // late answers still help the next scan
            try { cache.put(d.host(), assemble(d, claimed, dom.join(), page.join(), null)); }
            catch (Exception e) { log.debug("late site facts failed: {}", e.toString()); }
        }, pool);
        return f;
    }

    /** @param end deadline; the favicon is only fetched when 600 ms are left (null = late run: never) */
    private Facts assemble(NormalizedDomain d, Brand claimed, DomainIntelService.DomainFacts dom, PageFetcher.Page page, Instant end) {
        Instant now = Instant.now();
        List<Finding> out = new ArrayList<>();
        Brand brand = null;
        String finalUrl = null;
        if (dom != null) DomainIntelService.score(dom, now).ifPresent(out::add);
        if (page != null) {
            out.addAll(tls.assess(d, page.chain(), now));
            finalUrl = TlsRedirectService.finalUrl(page.chain());
            if (page.chain().hops().size() > 1 && finalUrl != null) {
                var endSite = lookalike.normalize(finalUrl);
                if (endSite != null && !endSite.registrable().equals(d.registrable()))
                    brand = lookalike.check(endSite, null).filter(m -> m.score() >= 60).map(m -> m.brand()).orElse(null);
            }
            if (page.features() != null) {
                var site = finalUrl == null ? d : lookalike.normalize(finalUrl);
                boolean time = end != null && Duration.between(Instant.now(), end).toMillis() > 600;
                String favicon = time && lookalike.registry().hasFavicons() ? faviconHash(page.features().faviconUrl()) : null;
                var res = new PageBrandCheck(lookalike.registry(), lookalike.normalizer())
                        .check(site, page.features(), claimed != null ? claimed : brand, favicon);
                out.addAll(res.findings());
                if (brand == null) brand = res.brand();
            }
        }
        return new Facts(List.copyOf(out), brand, finalUrl, dom != null && page != null);
    }

    private String faviconHash(String url) {
        var c = http.get(url, 2, Instant.now().plusMillis(500));
        var last = c.last();
        if (last == null || last.status() != 200 || last.body().length == 0 || last.truncated() || last.body().length > 100_000) return null;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(last.body()));
        } catch (Exception e) {
            return null;
        }
    }

    private static <T> T now(CompletableFuture<T> f, Instant end) {
        try {
            long ms = Duration.between(Instant.now(), end).toMillis();
            return ms <= 0 ? f.getNow(null) : f.get(ms, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException e) {
            return null;
        }
    }

    private static Instant min(Instant a, Instant b) { return a.isBefore(b) ? a : b; }
}
