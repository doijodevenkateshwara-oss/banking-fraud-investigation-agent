package com.bfs.fraudcase.agent.common;

/**
 * The configured model or provider could not produce a response. Message is safe to display.
 *
 * @author Venkateshwara Doijode
 */
public class ProcessorUnavailableException extends RuntimeException {

    public ProcessorUnavailableException(String safeMessage, Throwable cause) {
        super(safeMessage, cause);
    }
}
