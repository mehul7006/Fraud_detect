package com.fraudshield.blocking;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * 18.2: GET /api/v1/config/blocklists?variant=play|web -> signed meta (version, count, sha256, signature);
 * GET /api/v1/config/blocklists/file?variant=play|web -> the FSBL1 file (no domain names inside, hashes only).
 * Authenticated like every /config endpoint. Nothing about the phone is recorded.
 */
@RestController
@RequestMapping("/api/v1/config/blocklists")
public class BlocklistFileController {
    private final BlocklistFileService lists;

    public BlocklistFileController(BlocklistFileService lists) { this.lists = lists; }

    @GetMapping
    public BlocklistFileService.Meta meta(@RequestParam(defaultValue = "play") String variant) {
        return lists.meta(variant(variant));
    }

    @GetMapping("/file")
    public ResponseEntity<byte[]> file(@RequestParam(defaultValue = "play") String variant) {
        byte[] b = lists.file(variant(variant));
        if (b == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no block list published");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .cacheControl(CacheControl.noCache().noTransform()).body(b);
    }

    private static String variant(String v) {
        try {
            return BlocklistFileService.normaliseVariant(v);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
