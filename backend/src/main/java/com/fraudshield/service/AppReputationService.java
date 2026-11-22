package com.fraudshield.service;

import com.fraudshield.domain.AppReputation;
import com.fraudshield.repo.Repositories.AppReputationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** 6.2: known-good / known-bad apps. Used by the admin API, the CSV bulk import and the start-up seed. */
@Service
public class AppReputationService {
    public record ImportResult(int added, int updated, int skipped, List<String> errors) {}

    static final int MAX_IMPORT_ROWS = 5000;
    /** Android package name: at least two segments, each starting with a letter. */
    private static final Pattern PACKAGE = Pattern.compile("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+");

    private final AppReputationRepository repo;
    public AppReputationService(AppReputationRepository repo) { this.repo = repo; }

    /** Adds or changes one entry. overwrite=false keeps an existing row untouched (seed must never undo admin edits). */
    @Transactional
    public AppReputation set(String pkg, String label, String verdict, String cert, String source, boolean overwrite) {
        String p = pkg == null ? "" : pkg.trim();
        if (p.length() > 200 || !PACKAGE.matcher(p).matches())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid package name '" + p + "'");
        String v = verdict == null || verdict.isBlank() ? "GOOD" : verdict.trim().toUpperCase(Locale.ROOT);
        if (!v.equals("GOOD") && !v.equals("BAD")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "verdict must be GOOD or BAD");
        if (cert != null && !cert.isBlank() && !AppTrust.validCerts(cert))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "certSha256 must be SHA-256 hex (64 characters)");
        String l = label == null || label.isBlank() ? null : label.trim().substring(0, Math.min(120, label.trim().length()));

        var existing = repo.findByPackageName(p);
        if (existing.isPresent() && !overwrite) return existing.get();
        AppReputation a = existing.orElseGet(() -> new AppReputation(p, l, v, source));
        a.update(l != null ? l : a.getLabel(), v, source);
        if (cert != null && !cert.isBlank()) a.setCertSha256(AppTrust.normalise(cert));
        return repo.save(a);
    }

    /**
     * CSV columns: package_name,label,verdict,cert_sha256 (only package_name required; verdict defaults to GOOD).
     * Labels must not contain commas. Lines starting with # and a header line are ignored. Bad rows are skipped and
     * reported, never half-applied. Capped at 5000 rows.
     */
    @Transactional
    public ImportResult importCsv(String csv, String source, boolean overwrite) {
        int added = 0, updated = 0, skipped = 0, row = 0;
        List<String> errors = new ArrayList<>();
        for (String line : csv.split("\\r?\\n")) {
            row++;
            if (line.isBlank() || line.startsWith("#") || line.toLowerCase(Locale.ROOT).startsWith("package_name")) continue;
            if (added + updated + skipped >= MAX_IMPORT_ROWS) { errors.add("Stopped at " + MAX_IMPORT_ROWS + " rows (limit)"); break; }
            String[] p = line.split(",", -1);
            String pkg = p[0].trim();
            try {
                boolean existed = repo.findByPackageName(pkg).isPresent();
                if (existed && !overwrite) { skipped++; continue; }
                set(pkg, p.length > 1 ? p[1] : null, p.length > 2 ? p[2] : null, p.length > 3 ? p[3] : null, source, overwrite);
                if (existed) updated++; else added++;
            } catch (ResponseStatusException e) {
                skipped++;
                if (errors.size() < 50) errors.add("Line " + row + ": " + e.getReason());
            }
        }
        return new ImportResult(added, updated, skipped, errors);
    }
}
