package com.fraudshield.siteintel;

import java.net.URI;
import java.time.Instant;
import java.util.Locale;

/**
 * 10.7: safe page fetch for the link checker. Privacy: the query string and fragment are dropped before fetching
 * (they may carry the user's tokens / one-time codes); only scheme, host and path are requested, from the server,
 * without cookies. Pages over 1 MB are cut; non-HTML answers are not parsed.
 */
public final class PageFetcher {
    public record Page(SafeHttp.Chain chain, PageFeatures features) {}

    private final SafeHttp http;

    public PageFetcher(SafeHttp http) { this.http = http; }

    public static String withoutQuery(String url) {
        try {
            URI u = URI.create(url.contains("://") ? url.strip() : "http://" + url.strip());
            if (u.getRawAuthority() == null) return url;
            String auth = u.getRawAuthority().substring(u.getRawAuthority().lastIndexOf('@') + 1); // never send user:password@
            return u.getScheme() + "://" + auth + (u.getRawPath() == null || u.getRawPath().isEmpty() ? "/" : u.getRawPath());
        } catch (Exception e) {
            return url;
        }
    }

    public Page fetch(String url, Instant deadline) {
        var chain = http.get(withoutQuery(url), TlsRedirectService.MAX_REDIRECTS, deadline);
        var last = chain.last();
        PageFeatures f = null;
        if (last != null && last.status() >= 200 && last.status() < 300 && last.body().length > 0) {
            String ct = last.header("content-type");
            boolean html = ct == null || ct.toLowerCase(Locale.ROOT).contains("html") || looksLikeHtml(last.body());
            if (html) f = PageFeatures.parse(last.body(), ct, last.url().toString());
        }
        return new Page(chain, f);
    }

    private static boolean looksLikeHtml(byte[] b) {
        String head = new String(b, 0, Math.min(b.length, 512), java.nio.charset.StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT);
        return head.contains("<html") || head.contains("<!doctype html") || head.contains("<form");
    }
}
