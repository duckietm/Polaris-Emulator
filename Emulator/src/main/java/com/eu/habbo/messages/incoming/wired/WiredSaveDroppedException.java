package com.eu.habbo.messages.incoming.wired;

/** A save AIR's server drops without any answer: no saved packet and no error. */
public class WiredSaveDroppedException extends WiredSaveException {
    public WiredSaveDroppedException(String message) {
        super(message);
    }
}
