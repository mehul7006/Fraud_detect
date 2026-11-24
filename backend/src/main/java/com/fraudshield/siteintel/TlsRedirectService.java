package com.fraudshield.siteintel;

import com.fraudshield.lookalike.LookalikeService;
import com.fraudshield.lookalike.NormalizedDomain;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 10.6: certificate and redirect facts of a fetched link (from {@link SafeHttp.Chain}).
 * Wrong-host / untrusted certificate, a certificate issued in the last days, more than 5 redirects, and a chain
 * that ends on a different site - judged again with the look-alike engine, so a short link to "fleepkart.com" is
 * caught even though the first address looked harmless.
 */
public final class TlsRedirectService {
    public static final int MAX_REDIRECTS = 5;
    private final LookalikeService lookalike;

    public TlsRedirectService(LookalikeService lookalike) { this.lookalike = lookalike; }

    /** Where the link really ends, or null if it could not be followed. */
    public static String finalUrl(SafeHttp.Chain c) {
        var last = c.last();
        return last == null ? null : last.url().toString();
    }

    public List<Finding> assess(NormalizedDomain first, SafeHttp.Chain chain, Instant now) {
        List<Finding> out = new ArrayList<>();
        if (chain.blocked()) {
            out.add(new Finding(40, "PRIVATE_ADDRESS", "Link points into a private / local network"));
            return out;
        }
        var firstHop = chain.first();
        if (firstHop != null && firstHop.tls() != null) {
            var t = firstHop.tls();
            if (Boolean.FALSE.equals(t.trusted()))
                out.add(new Finding(25, "CERT_UNTRUSTED", "The site's security certificate is not valid"));
            else if (Boolean.FALSE.equals(t.hostnameMatches()))
                out.add(new Finding(30, "CERT_MISMATCH", "The site's security certificate belongs to a different site"));
            else if (t.ageDays(now) >= 0 && t.ageDays(now) < 7 && !lookalike.isPopular(first.registrable()))
                out.add(new Finding(10, "NEW_CERTIFICATE", "The site's certificate was issued only " + t.ageDays(now) + " day(s) ago"));
        }
        if (chain.tooManyRedirects())
            out.add(new Finding(15, "TOO_MANY_REDIRECTS", "Link bounces through more than " + MAX_REDIRECTS + " other addresses"));

        var last = chain.last();
        if (last != null && chain.hops().size() > 1) {
            var end = lookalike.normalize(last.url().toString());
            if (end != null && !end.registrable().equals(first.registrable())) {
                var official = lookalike.registry().officialBrand(end.registrable());
                if (official.isEmpty() && !lookalike.isPopular(end.registrable())) {
                    out.add(new Finding(15, "REDIRECTS_ELSEWHERE", "Link secretly leads to a different site: " + end.registrable()));
                    lookalike.check(end, last.url().getRawPath()).filter(m -> m.score() >= 60).ifPresent(m ->
                            out.add(new Finding(m.score(), "LOOKALIKE_BRAND",
                                    "Leads to " + end.registrable() + ", which looks like " + m.brand().name() + " but is not: " + m.reason())));
                }
            }
            // last hop on plain http after starting on https: downgrade
            if (first != null && firstHop != null && firstHop.tls() != null && last.tls() == null)
                out.add(new Finding(10, "HTTPS_DOWNGRADE", "Link moves you from a secure (https) to an insecure page"));
        }
        return out;
    }
}
