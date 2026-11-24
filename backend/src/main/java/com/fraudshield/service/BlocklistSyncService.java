package com.fraudshield.service;

import com.fraudshield.domain.BlocklistChange;
import com.fraudshield.repo.Repositories.BlocklistChangeRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 12.5: hashed domain blocklist for the phone's VPN filter, as signed deltas.
 * The phone keeps its version and asks for changes since then; it rejects any answer whose ECDSA P-256 signature
 * (over {@link #canonical}) does not verify with the public key built into the app.
 */
@Service
public class BlocklistSyncService {
    private static final Logger log = LoggerFactory.getLogger(BlocklistSyncService.class);
    static final int MAX_DELTA = 50_000;

    /** {@code full}: the phone must replace its list (first sync, or too far behind). */
    public record Delta(long version, boolean full, List<String> added, List<String> removed, String signature) {}

    private final BlocklistChangeRepository changes;
    private final PrivateKey key;
    private final PublicKey publicKey;

    public BlocklistSyncService(BlocklistChangeRepository changes,
                                @Value("${fraudshield.blocklist.signing-key-file:}") String keyFile) {
        this.changes = changes;
        KeyPair kp = load(keyFile);
        this.key = kp.getPrivate(); this.publicKey = kp.getPublic();
    }

    public static String hash(String domain) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(domain.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public void recordAdd(String domain) { changes.save(new BlocklistChange(hash(domain), "ADD")); }
    public void recordRemove(String domain) { changes.save(new BlocklistChange(hash(domain), "REMOVE")); }

    public Delta since(long since) {
        long latest = changes.findTopByOrderByVersionDesc().map(BlocklistChange::getVersion).orElse(0L);
        List<BlocklistChange> rows = since <= 0 || since > latest ? changes.findAllByOrderByVersionAsc()
                : changes.findByVersionGreaterThanOrderByVersionAsc(since);
        boolean full = since <= 0 || since > latest || rows.size() > MAX_DELTA;
        if (full && since > 0 && rows.size() > MAX_DELTA) rows = changes.findAllByOrderByVersionAsc();
        // replay in order: the last operation per hash wins
        Map<String, String> last = new LinkedHashMap<>();
        for (var c : rows) { last.remove(c.getDomainHash()); last.put(c.getDomainHash(), c.getOp()); }
        List<String> added = new ArrayList<>(), removed = new ArrayList<>();
        last.forEach((h, op) -> { if (op.equals("ADD")) added.add(h); else if (!full) removed.add(h); });
        Collections.sort(added); Collections.sort(removed);
        return new Delta(latest, full, added, removed, sign(canonical(latest, full, added, removed)));
    }

    /** What is signed. The app rebuilds exactly this string. */
    public static String canonical(long version, boolean full, List<String> added, List<String> removed) {
        return "fraudshield-blocklist-v1\n" + version + "\n" + full + "\n" + String.join(",", added) + "\n" + String.join(",", removed);
    }

    /** Same key for the blocklist (12.5), domain categories (13.6) and number list (16.4). */
    public String sign(String text) {
        try {
            Signature s = Signature.getInstance("SHA256withECDSA");
            s.initSign(key);
            s.update(text.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(s.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Base64 X.509 public key - only known for the temporary dev key (the real one ships inside the app). */
    public String publicKeyBase64() { return publicKey == null ? null : Base64.getEncoder().encodeToString(publicKey.getEncoded()); }

    /** PKCS#8 PEM private key file; none = a temporary key pair (dev / tests), which phones will reject. */
    private static KeyPair load(String file) {
        try {
            if (file != null && !file.isBlank()) {
                String pem = Files.readString(Path.of(file)).replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
                PrivateKey priv = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
                return new KeyPair(null, priv);
            }
            log.warn("No fraudshield.blocklist.signing-key-file: temporary signing key - phones will reject the blocklist");
            KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
            g.initialize(new ECGenParameterSpec("secp256r1"));
            return g.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("blocklist signing key unreadable: " + e.getMessage(), e);
        }
    }
}
