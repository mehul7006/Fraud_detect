package com.fraudshield.logging;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/** 8.4: %m / %msg / %message with personal data removed (see LogScrubber). */
public class ScrubbingMessageConverter extends MessageConverter {
    @Override
    public String convert(ILoggingEvent event) { return LogScrubber.scrub(super.convert(event)); }
}
