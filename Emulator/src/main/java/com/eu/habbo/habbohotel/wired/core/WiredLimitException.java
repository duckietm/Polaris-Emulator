package com.eu.habbo.habbohotel.wired.core;

/**
 * Exception thrown when wired execution exceeds configured limits.
 * <p>
 * This is a safety mechanism to prevent infinite loops or excessive
 * resource consumption in wired stacks. When thrown, the current
 * stack execution is aborted and an error is logged.
 * </p>
 * 
 * <h3>Common Causes:</h3>
 * <ul>
 *   <li>Infinite loops via "Trigger Stacks" effect</li>
 *   <li>Excessively complex wired setups</li>
 *   <li>Malicious room configurations</li>
 * </ul>
 * 
 * @see WiredState#step()
 * @see WiredEngine
 */
public final class WiredLimitException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;

    /**
     * Create a new limit exception with a message.
     * @param message description of the limit violation
     */
    public WiredLimitException(String message) {
        super(message);
    }

    /**
     * Create a new limit exception with a message and cause.
     * @param message description of the limit violation
     * @param cause the underlying cause
     */
    public WiredLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
