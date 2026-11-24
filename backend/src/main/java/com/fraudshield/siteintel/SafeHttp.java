package com.fraudshield.siteintel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import javax.net.ssl.*;

/**
 * 10.6 / 10.7: a deliberately small HTTP/1.1 client for looking at untrusted sites.
 * <ul>
 *   <li>every hop: host checked by {@link AddressGuard}, connection made to that checked address (no second DNS
 *   lookup, so DNS rebinding cannot redirect us to an internal address)</li>
 *   <li>GET only, no cookies, no auth, no JavaScript, {@code Accept-Encoding: identity}, body capped (1 MB)</li>
 *   <li>one deadline for the whole chain (redirects included)</li>
 *   <li>TLS: the certificate is always recorded; a wrong host name or untrusted chain is a finding, not an error -
 *   we still read the page (we never send anything to it)</li>
 * </ul>
 */
public final class SafeHttp {
    /** What the certificate tells us. {@code hostnameMatches} / {@code trusted} are null for plain http. */
    public record TlsFacts(String issuer, String subject, Instant notBefore, Instant notAfter, Boolean trusted,
                           Boolean hostnameMatches, String problem) {
        public long ageDays(Instant now) { return notBefore == null ? -1 : Duration.between(notBefore, now).toDays(); }
    }

    /** One request / response. */
    public record Hop(URI url, int status, Map<String, String> headers, byte[] body, boolean truncated, TlsFacts tls) {
        public String header(String name) { return headers.get(name.toLowerCase(Locale.ROOT)); }
        public boolean redirect() { return status >= 300 && status < 400 && header("location") != null; }
    }

    /** Result of following a link. {@code blocked} = refused by the SSRF guard (reason in {@code error}). */
    public record Chain(List<Hop> hops, boolean tooManyRedirects, boolean blocked, String error) {
        public Hop first() { return hops.isEmpty() ? null : hops.get(0); }
        public Hop last() { return hops.isEmpty() ? null : hops.get(hops.size() - 1); }
    }

    public static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Mobile FraudShieldLinkCheck/1.0";

    private final AddressGuard guard;
    private final int connectTimeoutMs, maxBytes;
    private final X509ExtendedTrustManager trust;

    public SafeHttp(AddressGuard guard, int connectTimeoutMs, int maxBytes, X509ExtendedTrustManager trust) {
        this.guard = guard; this.connectTimeoutMs = connectTimeoutMs; this.maxBytes = maxBytes;
        this.trust = trust == null ? systemTrust() : trust;
    }

    public static SafeHttp standard() { return new SafeHttp(AddressGuard.strict(), 1500, 1 << 20, null); }

    /** Follows up to {@code maxRedirects} redirects; never throws. */
    public Chain get(String url, int maxRedirects, Instant deadline) {
        List<Hop> hops = new ArrayList<>();
        URI u;
        try { u = URI.create(url.contains("://") ? url : "http://" + url); }
        catch (IllegalArgumentException e) { return new Chain(hops, false, false, "bad url"); }
        for (int i = 0; i <= maxRedirects; i++) {
            try {
                Hop h = one(u, deadline);
                hops.add(h);
                if (!h.redirect()) return new Chain(hops, false, false, null);
                u = u.resolve(h.header("location").strip().replace(" ", "%20"));
            } catch (AddressGuard.BlockedAddressException e) {
                return new Chain(hops, false, true, e.getMessage());
            } catch (Exception e) {
                return new Chain(hops, false, false, e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
        return new Chain(hops, true, false, "more than " + maxRedirects + " redirects");
    }

    private Hop one(URI u, Instant deadline) throws Exception {
        String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) throw new AddressGuard.BlockedAddressException("scheme " + scheme);
        String host = u.getHost();
        if (host == null) throw new IOException("no host");
        boolean tls = scheme.equals("https");
        int port = u.getPort() > 0 ? u.getPort() : tls ? 443 : 80;
        InetAddress ip = guard.check(host, port);

        try (Socket raw = new Socket()) {
            raw.connect(new InetSocketAddress(ip, port), Math.max(1, (int) Math.min(connectTimeoutMs, left(deadline))));
            raw.setSoTimeout(Math.max(1, (int) left(deadline)));
            Socket s = raw;
            TlsFacts facts = null;
            if (tls) {
                var rec = new RecordingTrust(trust);
                SSLContext ctx = SSLContext.getInstance("TLS");
                ctx.init(null, new TrustManager[]{rec}, null);
                SSLSocket ssl = (SSLSocket) ctx.getSocketFactory().createSocket(raw, host, port, false);
                SSLParameters p = ssl.getSSLParameters();
                p.setEndpointIdentificationAlgorithm("HTTPS"); // makes the trust manager check the host name
                if (!host.matches("[\\d.]+") && !host.contains(":")) p.setServerNames(List.of(new SNIHostName(host)));
                ssl.setSSLParameters(p);
                ssl.startHandshake();
                facts = rec.facts();
                s = ssl;
            }
            String path = u.getRawPath() == null || u.getRawPath().isEmpty() ? "/" : u.getRawPath();
            if (u.getRawQuery() != null) path += "?" + u.getRawQuery();
            String hostHeader = (host.contains(":") ? "[" + host + "]" : host) + (u.getPort() > 0 ? ":" + u.getPort() : "");
            OutputStream out = s.getOutputStream();
            out.write(("GET " + path + " HTTP/1.1\r\nHost: " + hostHeader + "\r\nUser-Agent: " + USER_AGENT
                    + "\r\nAccept: text/html,application/xhtml+xml,*/*;q=0.8\r\nAccept-Language: en-IN,en;q=0.9,hi;q=0.8"
                    + "\r\nAccept-Encoding: identity\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
            return read(u, s.getInputStream(), facts, deadline, s);
        }
    }

    private Hop read(URI u, InputStream in, TlsFacts tls, Instant deadline, Socket s) throws IOException {
        String status = line(in, 8192);
        String[] sp = status.split(" ", 3);
        if (sp.length < 2 || !sp[0].startsWith("HTTP/")) throw new IOException("not HTTP");
        int code = Integer.parseInt(sp[1].trim());
        Map<String, String> headers = new LinkedHashMap<>();
        int total = 0;
        for (String l; !(l = line(in, 8192)).isEmpty(); ) {
            total += l.length();
            if (total > 64 * 1024) throw new IOException("headers too big");
            int c = l.indexOf(':');
            if (c > 0) headers.merge(l.substring(0, c).strip().toLowerCase(Locale.ROOT), l.substring(c + 1).strip(), (a, b) -> a + ", " + b);
        }
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        boolean truncated = false;
        if (code >= 300 && code < 400 || code == 204 || code == 304) return new Hop(u, code, headers, new byte[0], false, tls);
        try {
            if ("chunked".equalsIgnoreCase(headers.getOrDefault("transfer-encoding", ""))) {
                while (true) {
                    s.setSoTimeout(Math.max(1, (int) left(deadline)));
                    String sizeLine = line(in, 1024);
                    int semi = sizeLine.indexOf(';');
                    int size = Integer.parseInt((semi >= 0 ? sizeLine.substring(0, semi) : sizeLine).strip(), 16);
                    if (size == 0) break;
                    if (body.size() + size > maxBytes) { copy(in, body, maxBytes - body.size()); truncated = true; break; }
                    copy(in, body, size);
                    line(in, 16);
                }
            } else {
                long len = headers.containsKey("content-length") ? Long.parseLong(headers.get("content-length").split(",")[0].strip()) : Long.MAX_VALUE;
                long want = Math.min(len, maxBytes);
                copy(in, body, want);
                truncated = len > maxBytes || (len == Long.MAX_VALUE && body.size() >= maxBytes && in.read() >= 0);
            }
        } catch (SocketTimeoutException e) {
            truncated = true; // deadline reached: use what we have
        }
        return new Hop(u, code, headers, body.toByteArray(), truncated, tls);
    }

    private static void copy(InputStream in, ByteArrayOutputStream out, long n) throws IOException {
        byte[] buf = new byte[8192];
        while (n > 0) {
            int r = in.read(buf, 0, (int) Math.min(buf.length, n));
            if (r < 0) return;
            out.write(buf, 0, r);
            n -= r;
        }
    }

    private static String line(InputStream in, int max) throws IOException {
        StringBuilder b = new StringBuilder();
        int c;
        while ((c = in.read()) >= 0) {
            if (c == '\n') break;
            if (c != '\r') b.append((char) c);
            if (b.length() > max) throw new IOException("line too long");
        }
        if (c < 0 && b.isEmpty()) throw new IOException("connection closed");
        return b.toString();
    }

    private static long left(Instant deadline) {
        long ms = Duration.between(Instant.now(), deadline).toMillis();
        if (ms <= 0) throw new DeadlineException();
        return ms;
    }

    static final class DeadlineException extends RuntimeException {
        DeadlineException() { super("time budget used up"); }
    }

    static X509ExtendedTrustManager systemTrust() {
        try {
            TrustManagerFactory f = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            f.init((KeyStore) null);
            for (TrustManager t : f.getTrustManagers()) if (t instanceof X509ExtendedTrustManager x) return x;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        throw new IllegalStateException("no X509 trust manager");
    }

    /**
     * Wraps the real trust manager: runs the normal checks (chain + host name), records the outcome and the
     * certificate, and lets the handshake continue either way.
     */
    static final class RecordingTrust extends X509ExtendedTrustManager {
        private final X509ExtendedTrustManager real;
        private X509Certificate leaf;
        private Boolean trusted, hostOk;
        private String problem;

        RecordingTrust(X509ExtendedTrustManager real) { this.real = real; }

        TlsFacts facts() {
            if (leaf == null) return new TlsFacts(null, null, null, null, false, false, "no certificate");
            return new TlsFacts(leaf.getIssuerX500Principal().getName(), leaf.getSubjectX500Principal().getName(),
                    leaf.getNotBefore().toInstant(), leaf.getNotAfter().toInstant(), trusted, hostOk, problem);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {
            leaf = chain.length > 0 ? chain[0] : null;
            try {
                real.checkServerTrusted(chain, authType, socket); // chain AND host name
                trusted = true; hostOk = true;
            } catch (CertificateException full) {
                try {
                    real.checkServerTrusted(chain, authType);     // chain only
                    trusted = true; hostOk = false; problem = "certificate is for a different site";
                } catch (CertificateException chainOnly) {
                    trusted = false; hostOk = null; // unknown: the chain itself already failed
                    problem = "certificate not trusted (" + shortMsg(chainOnly) + ")";
                }
            }
        }

        private static String shortMsg(Exception e) {
            String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return m.length() > 80 ? m.substring(0, 80) : m;
        }

        @Override public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) { checkServerTrusted(chain, authType, (Socket) null); }
        @Override public void checkServerTrusted(X509Certificate[] chain, String authType) { checkServerTrusted(chain, authType, (Socket) null); }
        @Override public void checkClientTrusted(X509Certificate[] c, String a, Socket s) throws CertificateException { throw new CertificateException("client auth not used"); }
        @Override public void checkClientTrusted(X509Certificate[] c, String a, SSLEngine e) throws CertificateException { throw new CertificateException("client auth not used"); }
        @Override public void checkClientTrusted(X509Certificate[] c, String a) throws CertificateException { throw new CertificateException("client auth not used"); }
        @Override public X509Certificate[] getAcceptedIssuers() { return real.getAcceptedIssuers(); }
    }
}
