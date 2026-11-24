package com.fraudshield.blocking;

import com.fraudshield.service.BlocklistSyncService;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 18.2 / 18.3: the phone-wide ad / tracker / malware block list. Built offline by ml/blocklists/build_blocklists.py
 * (licence-checked sources) into two files in {@code fraudshield.blocklists.dir}:
 * <ul>
 *   <li>{@code blocklists-play.bin} - trackers + malware only (Google Play flavour: Play forbids blocking other apps' ads)</li>
 *   <li>{@code blocklists-web.bin}  - + ads (website flavour)</li>
 * </ul>
 * Format FSBL1: "FSBL1" | version u64 BE | count u32 BE | count x (hash i64 BE, category u8: 1 ads, 2 trackers,
 * 3 malware), sorted by signed hash; hash = first 8 bytes of SHA-256(lower-case domain). The file holds no domain
 * names. The phone downloads a variant when its version changes and checks the ECDSA signature (same key as 12.5)
 * over {@link #canonical} plus the file's SHA-256 before using it.
 * <p>Files are re-read when they change on disk (a weekly rebuild needs no restart). No file = count 0 (the phone keeps
 * what it has).
 */
@Service
public class BlocklistFileService {
    private static final Logger log = LoggerFactory.getLogger(BlocklistFileService.class);
    public static final String[] VARIANTS = {"play", "web"};

    public record Meta(String variant, long version, int count, long size, String sha256, Map<String, Integer> categories,
                       String signature) {}

    private record Loaded(long mtime, byte[] bytes, Meta meta) {}

    private final Path dir;
    private final BlocklistSyncService signer; // null in plain unit tests
    private final Map<String, Loaded> cache = new ConcurrentHashMap<>();

    @Autowired
    public BlocklistFileService(@Value("${fraudshield.blocklists.dir:blocklists}") String dir, BlocklistSyncService signer) {
        this.dir = Path.of(dir);
        this.signer = signer;
        for (String v : VARIANTS) {
            Meta m = meta(v);
            if (m.count() > 0) log.info("Block list {}: {} domains, version {}", v, m.count(), m.version());
            else log.info("Block list {}: no file in {} (phones keep their current list)", v, this.dir.toAbsolutePath());
        }
    }

    public static String canonical(String variant, long version, int count, String sha256) {
        return "fraudshield-blocklists-v1\n" + variant + "\n" + version + "\n" + count + "\n" + sha256;
    }

    public static String normaliseVariant(String v) {
        String x = v == null ? "play" : v.toLowerCase(Locale.ROOT);
        if (!x.equals("play") && !x.equals("web")) throw new IllegalArgumentException("variant must be play or web");
        return x;
    }

    public Meta meta(String variant) {
        Loaded l = load(normaliseVariant(variant));
        return l == null ? new Meta(variant, 0, 0, 0, null, Map.of(), null) : l.meta();
    }

    /** The file, or null when there is none. */
    public byte[] file(String variant) {
        Loaded l = load(normaliseVariant(variant));
        return l == null ? null : l.bytes();
    }

    private Loaded load(String variant) {
        Path p = dir.resolve("blocklists-" + variant + ".bin");
        try {
            if (!Files.isRegularFile(p)) { cache.remove(variant); return null; }
            long mtime = Files.getLastModifiedTime(p).toMillis();
            Loaded c = cache.get(variant);
            if (c != null && c.mtime() == mtime) return c;
            byte[] b = Files.readAllBytes(p);
            Meta m = check(variant, b);
            Loaded l = new Loaded(mtime, b, m);
            cache.put(variant, l);
            return l;
        } catch (IOException | IllegalStateException e) {
            log.warn("Block list {} unusable: {}", variant, e.getMessage());
            return cache.get(variant); // keep serving the last good one
        }
    }

    /** Validates the whole file (magic, size, sorted unique hashes, known categories) and builds its signed meta. */
    Meta check(String variant, byte[] b) {
        if (b.length < 17 || !new String(b, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).equals("FSBL1"))
            throw new IllegalStateException("not an FSBL1 file");
        ByteBuffer bb = ByteBuffer.wrap(b);
        bb.position(5);
        long version = bb.getLong();
        int count = bb.getInt();
        if (b.length != 17 + count * 9L) throw new IllegalStateException("size does not match count " + count);
        int[] cats = new int[4];
        long prev = Long.MIN_VALUE;
        for (int i = 0; i < count; i++) {
            long h = bb.getLong();
            int c = bb.get();
            if (i > 0 && h <= prev) throw new IllegalStateException("hashes not sorted / unique at " + i);
            if (c < 1 || c > 3) throw new IllegalStateException("unknown category " + c);
            if (variant.equals("play") && c == 1) throw new IllegalStateException("play list must not contain ads");
            cats[c]++;
            prev = h;
        }
        String sha;
        try {
            sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        String sig = signer == null ? null : signer.sign(canonical(variant, version, count, sha));
        return new Meta(variant, version, count, b.length, sha, Map.of("ads", cats[1], "trackers", cats[2], "malware", cats[3]), sig);
    }
}
