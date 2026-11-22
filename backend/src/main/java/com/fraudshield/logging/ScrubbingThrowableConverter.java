package com.fraudshield.logging;

import ch.qos.logback.classic.spi.IThrowableProxy;
import org.springframework.boot.logging.logback.ExtendedWhitespaceThrowableProxyConverter;

/** 8.4: stack traces (%wEx, %ex) with personal data removed from every exception message (see LogScrubber). */
public class ScrubbingThrowableConverter extends ExtendedWhitespaceThrowableProxyConverter {
    @Override
    protected String throwableProxyToString(IThrowableProxy tp) { return LogScrubber.scrub(super.throwableProxyToString(tp)); }
}
