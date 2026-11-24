package com.fraudshield.siteintel;

import com.sun.net.httpserver.*;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.concurrent.Executors;
import javax.net.ssl.*;

/** Local HTTP / HTTPS servers for the Phase 10 network tests. Nothing leaves the machine. */
final class StubServers {
    private StubServers() {}

    static HttpServer http() throws Exception {
        HttpServer s = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        s.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        s.start();
        return s;
    }

    /** HTTPS server whose certificate is for {@code certName} ("other.example" or "localhost"). */
    static HttpsServer https(String certName) throws Exception {
        HttpsServer s = HttpsServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        SSLContext ctx = SSLContext.getInstance("TLS");
        var kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore(certName), "changeit".toCharArray());
        ctx.init(kmf.getKeyManagers(), null, null);
        s.setHttpsConfigurator(new HttpsConfigurator(ctx));
        s.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        s.start();
        return s;
    }

    /** Trust manager that trusts exactly the test certificates (like a real CA would, for the host names in them). */
    static X509ExtendedTrustManager trusting(String... certNames) throws Exception {
        KeyStore ts = KeyStore.getInstance("PKCS12");
        ts.load(null, null);
        for (String n : certNames) ts.setCertificateEntry(n, (X509Certificate) keyStore(n).getCertificate("s"));
        var tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ts);
        for (TrustManager t : tmf.getTrustManagers()) if (t instanceof X509ExtendedTrustManager x) return x;
        throw new IllegalStateException();
    }

    private static KeyStore keyStore(String name) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = StubServers.class.getResourceAsStream("/tls/" + name + ".p12")) {
            ks.load(in, "changeit".toCharArray());
        }
        return ks;
    }

    static void reply(HttpServer s, String path, int status, String contentType, String body) {
        s.createContext(path, ex -> {
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", contentType);
            ex.sendResponseHeaders(status, b.length == 0 ? -1 : b.length);
            if (b.length > 0) ex.getResponseBody().write(b);
            ex.close();
        });
    }

    static void redirect(HttpServer s, String path, String location) {
        s.createContext(path, ex -> {
            ex.getResponseHeaders().add("Location", location);
            ex.sendResponseHeaders(302, -1);
            ex.close();
        });
    }

    static void slow(HttpServer s, String path, long ms, String body) {
        s.createContext(path, ex -> {
            try { Thread.sleep(ms); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "text/html");
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
    }

    /** A guard that maps the given test names to 127.0.0.1 and allows loopback (stub servers only). */
    static AddressGuard loopbackGuard(String... names) {
        return new AddressGuard(h -> {
            if (h.matches("[0-9.]+") || h.contains(":")) return new InetAddress[]{InetAddress.getByName(h)}; // IP literal
            for (String n : names) if (h.equals(n)) return new InetAddress[]{InetAddress.getLoopbackAddress()};
            if (h.equals("localhost") || h.equals("127.0.0.1")) return new InetAddress[]{InetAddress.getLoopbackAddress()};
            throw new java.net.UnknownHostException(h);
        }, true);
    }
}
