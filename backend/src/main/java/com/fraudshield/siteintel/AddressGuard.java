package com.fraudshield.siteintel;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Set;

/**
 * 10.6 / 10.7 SSRF guard: the server only ever connects to public internet addresses. Refused: loopback, private
 * (10/8, 172.16/12, 192.168/16), link-local incl. cloud metadata 169.254.169.254, carrier-grade NAT 100.64/10,
 * multicast, "this network" 0/8, benchmarking 198.18/15, documentation and reserved ranges, IPv6 unique-local fc00::/7,
 * link-local fe80::/10, IPv4-mapped forms of all of these. A host is refused when ANY of its addresses is refused
 * (split-horizon / rebinding names), and the caller connects to the checked address, never resolving twice.
 */
public final class AddressGuard {
    /** Lets tests swap DNS (e.g. a name that "rebinds" to 10.0.0.5). */
    public interface Resolver { InetAddress[] resolve(String host) throws UnknownHostException; }

    public static final Resolver SYSTEM_DNS = InetAddress::getAllByName;
    /** Only web ports: an attacker's link must not make us talk to mail / database ports on some public host. */
    static final Set<Integer> PORTS = Set.of(80, 443, 8080, 8443);

    private final Resolver resolver;
    private final boolean allowLoopback; // tests only: local stub servers

    public AddressGuard(Resolver resolver, boolean allowLoopback) { this.resolver = resolver; this.allowLoopback = allowLoopback; }

    public static AddressGuard strict() { return new AddressGuard(SYSTEM_DNS, false); }

    /** The address to connect to, or an exception that says why the host is refused. */
    public InetAddress check(String host, int port) throws BlockedAddressException, UnknownHostException {
        if (!PORTS.contains(port) && !(allowLoopback && port > 1024)) throw new BlockedAddressException("port " + port + " not allowed");
        String h = host.startsWith("[") ? host.substring(1, host.length() - 1) : host;
        if (h.equalsIgnoreCase("localhost") || h.endsWith(".localhost") || h.endsWith(".local") || h.endsWith(".internal")) {
            if (!allowLoopback) throw new BlockedAddressException("local name " + h);
        }
        InetAddress[] all = resolver.resolve(h);
        if (all.length == 0) throw new UnknownHostException(h);
        for (InetAddress a : all) {
            if (a.isLoopbackAddress() && allowLoopback) continue;
            if (!isPublic(a)) throw new BlockedAddressException("non-public address for " + h);
        }
        return all[0];
    }

    public static boolean isPublic(InetAddress a) {
        if (a.isLoopbackAddress() || a.isAnyLocalAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress()
                || a.isMulticastAddress()) return false;
        byte[] b = a.getAddress();
        if (a instanceof Inet6Address) {
            if ((b[0] & 0xfe) == 0xfc) return false;                        // fc00::/7 unique local
            if ((b[0] & 0xff) == 0xfe && (b[1] & 0xc0) == 0x80) return false; // fe80::/10
            if ((b[0] & 0xff) == 0x20 && (b[1] & 0xff) == 0x01 && (b[2] & 0xff) == 0x0d && (b[3] & 0xff) == 0xb8) return false; // 2001:db8::/32
            boolean mapped = true;                                           // ::ffff:a.b.c.d and ::a.b.c.d
            for (int i = 0; i < 10; i++) if (b[i] != 0) mapped = false;
            if (mapped && ((b[10] == (byte) 0xff && b[11] == (byte) 0xff) || (b[10] == 0 && b[11] == 0))) {
                try { return isPublic(InetAddress.getByAddress(new byte[]{b[12], b[13], b[14], b[15]})); }
                catch (UnknownHostException e) { return false; }
            }
            if ((b[0] & 0xff) == 0x20 && (b[1] & 0xff) == 0x02) {           // 2002::/16 6to4 wraps an IPv4 address
                try { return isPublic(InetAddress.getByAddress(new byte[]{b[2], b[3], b[4], b[5]})); }
                catch (UnknownHostException e) { return false; }
            }
            return true;
        }
        if (!(a instanceof Inet4Address)) return false;
        int o0 = b[0] & 0xff, o1 = b[1] & 0xff, o2 = b[2] & 0xff;
        if (o0 == 0 || o0 == 10 || o0 == 127 || o0 >= 224) return false;     // this-net, private, loopback, multicast+reserved
        if (o0 == 100 && o1 >= 64 && o1 <= 127) return false;                // 100.64/10 CGNAT
        if (o0 == 169 && o1 == 254) return false;                            // link-local, cloud metadata
        if (o0 == 172 && o1 >= 16 && o1 <= 31) return false;
        if (o0 == 192 && o1 == 168) return false;
        if (o0 == 192 && o1 == 0 && (o2 == 0 || o2 == 2)) return false;      // IETF protocol, TEST-NET-1
        if (o0 == 198 && (o1 == 18 || o1 == 19)) return false;               // benchmarking
        if (o0 == 198 && o1 == 51 && o2 == 100) return false;                // TEST-NET-2
        if (o0 == 203 && o1 == 0 && o2 == 113) return false;                 // TEST-NET-3
        return true;
    }

    public static final class BlockedAddressException extends Exception {
        public BlockedAddressException(String m) { super(m); }
    }
}
