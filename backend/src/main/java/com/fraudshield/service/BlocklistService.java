package com.fraudshield.service;

import com.fraudshield.domain.Blocklist;
import com.fraudshield.repo.Repositories.BlocklistRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
public class BlocklistService {
    public static final int MAX_IMPORT_ROWS = 5000;
    public record ImportResult(int added, int skipped, List<String> errors) {}

    private final BlocklistRepository repo;
    public BlocklistService(BlocklistRepository repo) { this.repo = repo; }

    public List<Blocklist> list() { return repo.findAll(); }

    /** Lowercase, trim, drop scheme/path/port for domains; SHA256 must be 64 hex characters. */
    static String normalise(String kind, String value) {
        String v = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if ("DOMAIN".equals(kind)) {
            v = v.replaceFirst("^[a-z][a-z0-9+.-]*://", "");
            int cut = v.length();
            for (char c : new char[] {'/', '?', '#', ':'}) { int i = v.indexOf(c); if (i >= 0) cut = Math.min(cut, i); }
            v = v.substring(0, cut);
            if (v.startsWith("www.")) v = v.substring(4);
            if (!v.matches("^[a-z0-9]([a-z0-9.-]*[a-z0-9])?\\.[a-z0-9-]{2,}$|^xn--[a-z0-9.-]+$"))
                throw new IllegalArgumentException("Not a valid domain");
        } else if ("SHA256".equals(kind)) {
            if (!v.matches("^[0-9a-f]{64}$")) throw new IllegalArgumentException("SHA256 must be 64 hex characters");
        } else {
            throw new IllegalArgumentException("kind must be DOMAIN or SHA256");
        }
        return v;
    }

    public Blocklist add(String kind, String value, String reason) {
        String k = kind == null ? "" : kind.trim().toUpperCase(Locale.ROOT);
        String v;
        try { v = normalise(k, value); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage()); }
        if (repo.findByKindAndValue(k, v).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already on blocklist");
        return repo.save(new Blocklist(k, v, trim(reason)));
    }

    /** Adds a domain if it is not there yet (used when closing feedback). */
    public void addDomainIfMissing(String host, String reason) {
        if (repo.findByKindAndValue("DOMAIN", host).isEmpty()) repo.save(new Blocklist("DOMAIN", host, trim(reason)));
    }

    public void delete(Long id) {
        if (!repo.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
        repo.deleteById(id);
    }

    /** CSV columns: kind,value,reason. Bad lines are skipped and reported; the whole file is capped at 5000 rows. */
    public ImportResult importCsv(String csv) {
        int added = 0, skipped = 0, row = 0;
        List<String> errors = new ArrayList<>();
        for (String line : csv.split("\\r?\\n")) {
            if (line.isBlank()) continue;
            row++;
            if (row > MAX_IMPORT_ROWS) { errors.add("Stopped at row " + MAX_IMPORT_ROWS + " (limit)"); break; }
            String[] p = line.split(",", 3);
            if (row == 1 && p[0].trim().equalsIgnoreCase("kind")) continue; // header
            if (p.length < 2) { skipped++; errors.add("Row " + row + ": need kind,value"); continue; }
            try { add(p[0], p[1], p.length > 2 ? p[2] : null); added++; }
            catch (ResponseStatusException e) { skipped++; errors.add("Row " + row + ": " + e.getReason()); }
        }
        return new ImportResult(added, skipped, errors);
    }

    private static String trim(String s) {
        if (s == null || s.isBlank()) return null;
        s = s.trim();
        return s.length() > 200 ? s.substring(0, 200) : s;
    }
}
