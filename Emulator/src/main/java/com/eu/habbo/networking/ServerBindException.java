package com.eu.habbo.networking;

public final class ServerBindException extends IllegalStateException {

    public ServerBindException(
            String name, String host, int port, Throwable cause) {
        super("Failed to start " + name + " on " + host + ":" + port, cause);
    }
}
