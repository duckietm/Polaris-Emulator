package com.eu.habbo.networking;

public final class ServerBindException extends IllegalStateException {
    public ServerBindException(String serverName, String host, int port, Throwable cause) {
        super("Failed to start " + serverName + " on " + host + ":" + port, cause);
    }
}
