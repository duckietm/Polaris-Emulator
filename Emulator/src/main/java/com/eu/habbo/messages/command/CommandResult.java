package com.eu.habbo.messages.command;

import com.eu.habbo.messages.rcon.RCONMessage;
import com.google.gson.JsonObject;

/**
 * Transport-neutral outcome of dispatching a single command through the
 * {@link CommandRegistry}.
 *
 * <p>The on-the-wire envelope is intentionally the exact {@code {status, message}}
 * shape that the RCON listener has always produced (see {@code protocol/rcon-contract.json}),
 * so RCON callers observe byte-identical responses. The extra {@link #known} flag is
 * metadata for callers such as an HTTP handler that want to map the outcome onto a
 * richer status space (e.g. an unknown command onto HTTP 404) without changing the
 * serialized body.
 */
public record CommandResult(boolean known, int status, String message) {

    public CommandResult {
        message = message == null ? "" : message;
    }

    static CommandResult of(int status, String message) {
        return new CommandResult(true, status, message);
    }

    static CommandResult unknownCommand() {
        return new CommandResult(false, RCONMessage.STATUS_ERROR, "unknown command");
    }

    static CommandResult failed() {
        return new CommandResult(true, RCONMessage.SYSTEM_ERROR, "command failed");
    }

    public boolean ok() {
        return this.status == RCONMessage.STATUS_OK;
    }

    /**
     * Serializes the stable {@code {status, message}} envelope. Field order matches
     * {@link RCONMessage.RCONMessageSerializer} so existing RCON clients are unaffected.
     */
    public String toResponseJson() {
        JsonObject object = new JsonObject();
        object.addProperty("status", this.status);
        object.addProperty("message", this.message);
        return object.toString();
    }
}
