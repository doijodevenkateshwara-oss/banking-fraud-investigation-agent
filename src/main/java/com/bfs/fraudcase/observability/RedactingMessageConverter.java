package com.bfs.fraudcase.observability;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.bfs.fraudcase.domain.Masking;

/**
 * Logback converter that redacts card-like digit runs from every log message.
 *
 * @author Venkateshwara Doijode
 */
public class RedactingMessageConverter extends MessageConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return Masking.redact(super.convert(event));
    }
}
