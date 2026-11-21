package com.fraudshield.service;

import com.fraudshield.domain.Enums.Verdict;
import com.fraudshield.dto.Dtos.FileScanRequest;
import com.fraudshield.dto.Dtos.FileScanResponse;
import com.fraudshield.repo.Repositories.BlocklistRepository;
import org.springframework.stereotype.Service;
import java.util.*;

/** Metadata-only file scan: the file itself never leaves the phone, only hash + name + first bytes + content flag names. */
@Service
public class FileScanService {
    private static final Set<String> EXEC_EXT = Set.of("apk", "xapk", "apks", "exe", "scr", "bat", "cmd", "js",
            "vbs", "jar", "msi", "lnk", "sh");
    private static final Set<String> DOC_EXT = Set.of("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "jpg",
            "jpeg", "png", "mp4", "txt");
    private static final Set<String> MACRO_EXT = Set.of("docm", "xlsm", "pptm", "xlam");

    private final BlocklistRepository blocklist;

    private final FileReputationClient reputation;

    @org.springframework.beans.factory.annotation.Autowired
    public FileScanService(BlocklistRepository blocklist, FileReputationClient reputation) {
        this.blocklist = blocklist; this.reputation = reputation;
    }

    public FileScanService(BlocklistRepository blocklist) { this(blocklist, sha -> Optional.empty()); }

    public FileScanResponse scan(FileScanRequest r) {
        if (blocklist.findByKindAndValue("SHA256", r.sha256().toLowerCase(Locale.ROOT)).isPresent()) {
            return new FileScanResponse(Verdict.MALICIOUS, 100, List.of("Known malicious file hash"), "DELETE");
        }
        List<String> reasons = new ArrayList<>();
        int score = 0;
        // 5.1: ask VirusTotal about the HASH only. Failure/limit/off = no change to the score.
        var vt = reputation.lookup(r.sha256().toLowerCase(Locale.ROOT));
        if (vt.isPresent() && vt.get().malicious() > 0) {
            int n = vt.get().malicious();
            score += n >= 3 ? 70 : 30;
            reasons.add("Flagged by " + n + " of " + vt.get().total() + " antivirus engines");
        }
        String name = r.fileName().toLowerCase(Locale.ROOT);
        String[] parts = name.split("\\.");
        String ext = parts.length > 1 ? parts[parts.length - 1] : "";

        if (EXEC_EXT.contains(ext)) {
            score += ext.startsWith("apk") || ext.equals("xapk") ? 55 : 45;
            reasons.add("Executable/installable file type ." + ext);
        }
        if (MACRO_EXT.contains(ext)) { score += 40; reasons.add("Office document with macros"); }
        // invoice.pdf.apk  /  photo.jpg.exe
        if (parts.length >= 3 && DOC_EXT.contains(parts[parts.length - 2]) && EXEC_EXT.contains(ext)) {
            score += 40; reasons.add("Double extension disguises executable as document");
        }
        // magic bytes vs. extension
        String hdr = r.headerHex() == null ? "" : r.headerHex().toLowerCase(Locale.ROOT);
        boolean isZip = hdr.startsWith("504b0304");
        boolean isPdf = hdr.startsWith("25504446");
        boolean isElfOrMz = hdr.startsWith("7f454c46") || hdr.startsWith("4d5a");
        if (ext.equals("pdf") && !isPdf && !hdr.isEmpty()) { score += 40; reasons.add("File claims PDF but content differs"); }
        if ((ext.equals("jpg") || ext.equals("png") || ext.equals("mp4")) && (isZip || isElfOrMz)) {
            score += 50; reasons.add("Media file contains archive/executable data");
        }
        if (isElfOrMz) { score += 30; reasons.add("Native executable header"); }
        if (ext.equals("pdf") && r.sizeBytes() < 2_000) { score += 15; reasons.add("Tiny PDF (likely link/launcher stub)"); }

        // 5.2: content flags sent by the app (names only, never content)
        Set<String> flags = new HashSet<>();
        if (r.flags() != null) r.flags().forEach(f -> flags.add(f == null ? "" : f.toUpperCase(Locale.ROOT)));
        if (flags.contains("PDF_LAUNCH")) { score += 70; reasons.add("PDF can launch a program"); }
        if (flags.contains("PDF_JS")) { score += 35; reasons.add("PDF contains JavaScript"); }
        if (flags.contains("PDF_OPENACTION")) { score += 15; reasons.add("PDF runs an action when opened"); }
        if (flags.contains("OFFICE_MACRO") && !MACRO_EXT.contains(ext)) { score += 45; reasons.add("Office document contains macros"); }

        String src = r.sourceApp() == null ? "" : r.sourceApp().toLowerCase(Locale.ROOT);
        if (EXEC_EXT.contains(ext) && (src.contains("whatsapp") || src.contains("telegram") || src.contains("sms"))) {
            score += 15; reasons.add("Installable file received via messaging app");
        }
        score = Math.min(100, score);
        Verdict v = Verdict.fromScore(score);
        return new FileScanResponse(v, score, reasons, v == Verdict.MALICIOUS ? "DELETE" : v == Verdict.SUSPICIOUS ? "WARN" : "NONE");
    }
}
