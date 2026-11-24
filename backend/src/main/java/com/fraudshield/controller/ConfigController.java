package com.fraudshield.controller;

import com.fraudshield.dto.Dtos.CarrierConfigResponse;
import com.fraudshield.service.CarrierConfigService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.Duration;

@RestController
@RequestMapping("/api/v1/config")
public class ConfigController {
    private final CarrierConfigService carriers;
    private final com.fraudshield.service.BlocklistSyncService blocklist;
    private final com.fraudshield.service.DomainCategoryService domains;

    public ConfigController(CarrierConfigService carriers, com.fraudshield.service.BlocklistSyncService blocklist,
                            com.fraudshield.service.DomainCategoryService domains) {
        this.carriers = carriers; this.blocklist = blocklist; this.domains = domains;
    }

    private com.fraudshield.service.OfficialAppService official;

    @org.springframework.beans.factory.annotation.Autowired
    void setOfficial(com.fraudshield.service.OfficialAppService official) { this.official = official; }

    /** 13.13: official apps (brand, package, category, certSha256 when verified) - the phone's fake-app check (13.12). */
    @GetMapping("/official-apps")
    public java.util.List<com.fraudshield.service.OfficialAppService.Official> officialApps() {
        return official.all();
    }

    /** 13.6: ads / analytics / tracker / malware domains, changes since the phone's version, ECDSA-signed (12.5 key). */
    @GetMapping("/domains")
    public com.fraudshield.service.DomainCategoryService.Delta domains(@org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") long since) {
        return domains.since(since);
    }

    /** 12.5: blocked domains as SHA-256 hashes, changes since the phone's version, ECDSA-signed. */
    @GetMapping("/blocklist")
    public com.fraudshield.service.BlocklistSyncService.Delta blocklist(@org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") long since) {
        return blocklist.since(since);
    }

    /** Carrier response patterns. Supports ETag: send If-None-Match to get 304 when nothing changed. */
    @GetMapping("/carriers")
    public ResponseEntity<CarrierConfigResponse> carriers(@RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        CarrierConfigResponse cfg = carriers.config();
        String etag = "\"" + cfg.version() + "\"";
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(304).eTag(etag).build();
        }
        return ResponseEntity.ok().eTag(etag).cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate()).body(cfg);
    }
}
