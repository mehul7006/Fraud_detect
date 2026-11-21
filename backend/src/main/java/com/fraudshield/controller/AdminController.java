package com.fraudshield.controller;

import com.fraudshield.domain.Blocklist;
import com.fraudshield.domain.UrlFeedback;
import com.fraudshield.dto.Dtos.*;
import com.fraudshield.repo.Repositories.UrlFeedbackRepository;
import com.fraudshield.service.BlocklistService;
import com.fraudshield.service.BlocklistService.ImportResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** ADMIN-only endpoints (enforced in SecurityConfig for /api/v1/admin/**). */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final BlocklistService blocklist;
    private final UrlFeedbackRepository feedback;

    public AdminController(BlocklistService blocklist, UrlFeedbackRepository feedback) {
        this.blocklist = blocklist; this.feedback = feedback;
    }

    @GetMapping("/blocklist")
    public List<BlocklistEntry> list() { return blocklist.list().stream().map(AdminController::dto).toList(); }

    @PostMapping("/blocklist")
    @ResponseStatus(HttpStatus.CREATED)
    public BlocklistEntry add(@Valid @RequestBody BlocklistRequest r) {
        return dto(blocklist.add(r.kind(), r.value(), r.reason()));
    }

    @DeleteMapping("/blocklist/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { blocklist.delete(id); }

    @PostMapping(value = "/blocklist/import", consumes = "multipart/form-data")
    public ImportResult importCsv(@RequestParam("file") MultipartFile file) throws IOException {
        return blocklist.importCsv(new String(file.getBytes(), StandardCharsets.UTF_8));
    }

    @GetMapping("/feedback")
    public List<FeedbackEntry> queue(@RequestParam(defaultValue = "OPEN") String status) {
        return feedback.findByStatusOrderByCreatedAtAsc(status.toUpperCase()).stream().map(AdminController::dto).toList();
    }

    @PostMapping("/feedback/{id}/close")
    @Transactional
    public FeedbackEntry close(@PathVariable Long id, @Valid @RequestBody CloseFeedbackRequest r) {
        UrlFeedback f = feedback.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found"));
        if ("ADD_TO_BLOCKLIST".equals(r.resolution())) blocklist.addDomainIfMissing(f.getHost(), "Confirmed by user report");
        f.close(r.resolution());
        return dto(f);
    }

    private static BlocklistEntry dto(Blocklist b) { return new BlocklistEntry(b.getId(), b.getKind(), b.getValue(), b.getReason()); }
    private static FeedbackEntry dto(UrlFeedback f) {
        return new FeedbackEntry(f.getId(), f.getHost(), f.getKind(), f.getNote(), f.getStatus(), f.getResolution(), f.getCreatedAt());
    }
}
