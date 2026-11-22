package com.fraudshield;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;

/** 8.4 gate: with the real logging configuration, a grep of the log output finds no email / URL query / token. */
@SpringBootTest
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class LogScrubbingIT {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16");

    @Test
    void personalDataNeverReachesTheLog(CapturedOutput out) {
        var log = LoggerFactory.getLogger("org.hibernate.engine.jdbc.spi.SqlExceptionHelper"); // a framework logger
        log.error("ERROR: duplicate key value violates unique constraint. Detail: Key (email)=(victim@example.com) already exists.");
        log.warn("scan failed for https://phish.example/kyc?mobile=9876543210&otp=4711");
        log.error("Unhandled error", new IllegalStateException(
                "rejected Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI3In0.c2lnbmF0dXJlLXZhbHVl for victim@example.com"));

        assertThat(out.getAll())
            .contains("<email>", "https://phish.example/kyc?<query>", "<jwt>")
            .doesNotContain("victim@example.com", "mobile=9876543210", "otp=4711", "eyJhbGciOiJIUzI1NiJ9");
    }
}
