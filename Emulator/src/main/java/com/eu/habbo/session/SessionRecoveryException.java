package com.eu.habbo.session;

final class SessionRecoveryException extends RuntimeException {
    SessionRecoveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
