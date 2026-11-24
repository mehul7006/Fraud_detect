package com.fraudshield.siteintel;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetAddress;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 10.6 / 10.7 SSRF guard: private, local and metadata addresses are never fetched. */
class AddressGuardTest {

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "127.8.8.8", "10.0.0.5", "10.255.255.255", "172.16.0.1", "172.31.255.1",
            "192.168.1.1", "169.254.169.254", "169.254.0.1", "100.64.0.1", "100.127.255.254", "0.0.0.0", "0.1.2.3",
            "224.0.0.1", "255.255.255.255", "198.18.0.1", "192.0.2.10", "198.51.100.7", "203.0.113.9",
            "::1", "::", "fc00::1", "fd12:3456::1", "fe80::1", "ff02::1", "::ffff:10.0.0.1", "::ffff:127.0.0.1",
            "::ffff:169.254.169.254", "2002:0a00:0001::1", "2001:db8::1"})
    void private_and_reserved_addresses_are_refused(String ip) throws Exception {
        assertFalse(AddressGuard.isPublic(InetAddress.getByName(ip)), ip);
    }

    @ParameterizedTest
    @ValueSource(strings = {"8.8.8.8", "1.1.1.1", "103.21.244.1", "172.32.0.1", "100.128.0.1", "2606:4700:4700::1111", "::ffff:8.8.8.8"})
    void public_addresses_are_allowed(String ip) throws Exception {
        assertTrue(AddressGuard.isPublic(InetAddress.getByName(ip)), ip);
    }

    @Test
    void names_that_resolve_to_private_addresses_are_refused_dns_rebinding() {
        var guard = new AddressGuard(h -> switch (h) {
            case "rebind.test" -> new InetAddress[]{InetAddress.getByName("10.0.0.5")};
            case "split.test" -> new InetAddress[]{InetAddress.getByName("93.184.215.14"), InetAddress.getByName("169.254.169.254")};
            case "ok.test" -> new InetAddress[]{InetAddress.getByName("93.184.215.14")};
            default -> throw new java.net.UnknownHostException(h);
        }, false);
        assertThrows(AddressGuard.BlockedAddressException.class, () -> guard.check("rebind.test", 443));
        assertThrows(AddressGuard.BlockedAddressException.class, () -> guard.check("split.test", 443), "one bad address is enough");
        assertThrows(AddressGuard.BlockedAddressException.class, () -> guard.check("localhost", 80));
        assertThrows(AddressGuard.BlockedAddressException.class, () -> guard.check("printer.local", 80));
        assertThrows(AddressGuard.BlockedAddressException.class, () -> guard.check("ok.test", 22), "only web ports");
        assertDoesNotThrow(() -> guard.check("ok.test", 443));
    }

    /** The fetcher reports a link into a private network instead of opening it - also when a redirect leads there. */
    @Test
    void fetcher_never_connects_to_private_targets() throws Exception {
        var strict = new SafeHttp(AddressGuard.strict(), 500, 1 << 20, null);
        for (String u : new String[]{"http://127.0.0.1/", "http://169.254.169.254/latest/meta-data/", "http://10.1.2.3/",
                "http://[::1]/", "http://localhost/", "http://0x7f000001/", "file:///etc/passwd", "gopher://127.0.0.1/"}) {
            var c = strict.get(u, 5, Instant.now().plusSeconds(2));
            assertTrue(c.blocked() || (c.hops().isEmpty() && c.error() != null), u + " -> " + c);
            assertTrue(c.hops().isEmpty(), u + " must not be contacted");
        }
        // a public-looking site that redirects into the private network: the second hop is refused
        var s = StubServers.http();
        try {
            StubServers.redirect(s, "/go", "http://169.254.169.254/latest/meta-data/");
            var http = new SafeHttp(StubServers.loopbackGuard(), 500, 1 << 20, null);
            var c = http.get("http://localhost:" + s.getAddress().getPort() + "/go", 5, Instant.now().plusSeconds(2));
            assertEquals(1, c.hops().size());
            assertTrue(c.blocked(), c.toString());
        } finally {
            s.stop(0);
        }
    }
}
