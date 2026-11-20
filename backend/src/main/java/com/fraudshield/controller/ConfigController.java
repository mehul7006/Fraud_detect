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

    public ConfigController(CarrierConfigService carriers) { this.carriers = carriers; }

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
