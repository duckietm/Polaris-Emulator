package com.eu.habbo.habbohotel.messenger;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.DatabaseLoggable;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public class Message implements Runnable, DatabaseLoggable {

    private static final String QUERY = "INSERT INTO chatlogs_private (user_from_id, user_to_id, message, timestamp) VALUES (?, ?, ?, ?)";

    private final int fromId;
    private final int toId;
    private final int timestamp;
    private String message;

    public Message(int fromId, int toId, String message) {
        this(fromId, toId, message, Emulator.getIntUnixTimestamp());
    }

    public Message(int fromId, int toId, String message, int timestamp) {
        this.fromId = fromId;
        this.toId = toId;
        this.message = message;
        this.timestamp = timestamp;
    }

    @Override
    public void run() {
        if (Messenger.SAVE_PRIVATE_CHATS) {
            Emulator.getDatabaseLogger().store(this);
        }
    }

    @Override
    public String getQuery() {
        return QUERY;
    }

    @Override
    public void log(PreparedStatement statement) throws SQLException {
        statement.setInt(1, this.fromId);
        statement.setInt(2, this.toId);
        statement.setString(3, this.message);
        statement.setInt(4, this.timestamp);
        statement.addBatch();
    }

    public int getToId() {
        return this.toId;
    }

    public int getFromId() {
        return this.fromId;
    }

    public String getMessage() {
        return this.message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getTimestamp() {
        return this.timestamp;
    }
}
