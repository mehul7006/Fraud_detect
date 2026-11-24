package com.fraudshield.blocking;

import static org.junit.jupiter.api.Assertions.*;

import com.fraudshield.service.BlocklistSyncService;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 18.2: block-list files are validated, described and signed so the phone can trust them. */
public class BlocklistFileServiceTest {
    @TempDir Path dir;

    /** Same hashing as build_blocklists.py and BlockList.kt. */
    static long hash64(String domain) throws Exception {
        return ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(domain.getBytes(StandardCharsets.UTF_8))).getLong();
    }

    public static byte[] fsbl(long version, Map<String, Integer> entries) throws Exception {
        TreeMap<Long, Integer> rows = new TreeMap<>();
        for (var e : entries.entrySet()) rows.merge(hash64(e.getKey()), e.getValue(), Math::max);
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write("FSBL1".getBytes(StandardCharsets.US_ASCII));
        o.write(ByteBuffer.allocate(12).putLong(version).putInt(rows.size()).array());
        for (var r : rows.entrySet()) o.write(ByteBuffer.allocate(9).putLong(r.getKey()).put((byte) (int) r.getValue()).array());
        return o.toByteArray();
    }

    @Test
    void valid_files_are_described_and_signed() throws Exception {
        Files.write(dir.resolve("blocklists-web.bin"), fsbl(2026112401L, Map.of("ads.example.com", 1, "metrics.example.com", 2, "evil-sbi.top", 3)));
        Files.write(dir.resolve("blocklists-play.bin"), fsbl(2026112401L, Map.of("metrics.example.com", 2, "evil-sbi.top", 3)));
        var signer = new BlocklistSyncService(null, "");
        var s = new BlocklistFileService(dir.toString(), signer);

        var web = s.meta("web");
        assertEquals(3, web.count());
        assertEquals(2026112401L, web.version());
        assertEquals(Map.of("ads", 1, "trackers", 1, "malware", 1), web.categories());
        var play = s.meta("PLAY");
        assertEquals(0, play.categories().get("ads"), "the Play list never contains ads");
        assertEquals(17 + 2 * 9, s.file("play").length);

        // the phone's check: signature over the canonical text, and the file's SHA-256
        var key = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(signer.publicKeyBase64())));
        var v = Signature.getInstance("SHA256withECDSA");
        v.initVerify(key);
        v.update(BlocklistFileService.canonical("web", web.version(), web.count(), web.sha256()).getBytes(StandardCharsets.UTF_8));
        assertTrue(v.verify(Base64.getDecoder().decode(web.signature())));
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.file("web"))), web.sha256());
    }

    @Test
    void broken_files_are_refused_and_the_last_good_one_kept() throws Exception {
        var s = new BlocklistFileService(dir.toString(), null);
        assertEquals(0, s.meta("web").count(), "no file: count 0");
        assertNull(s.file("web"));

        assertThrows(IllegalStateException.class, () -> s.check("play", fsbl(1, Map.of("ads.example.com", 1))), "ads in play");
        byte[] good = fsbl(1, Map.of("a.example.com", 2, "b.example.com", 3));
        byte[] unsorted = good.clone();
        System.arraycopy(good, 17, unsorted, 26, 9);
        System.arraycopy(good, 26, unsorted, 17, 9);
        assertThrows(IllegalStateException.class, () -> s.check("web", unsorted));
        assertThrows(IllegalStateException.class, () -> s.check("web", Arrays.copyOf(good, good.length - 1)));
        assertThrows(IllegalStateException.class, () -> s.check("web", "NOPE!".getBytes()));
        assertThrows(IllegalArgumentException.class, () -> s.meta("other"));

        Path f = dir.resolve("blocklists-web.bin");
        Files.write(f, good);
        assertEquals(2, s.meta("web").count());
        Files.write(f, "garbage".getBytes());
        Files.setLastModifiedTime(f, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 5000));
        assertEquals(2, s.meta("web").count(), "a broken update does not replace the last good list");
    }
}
