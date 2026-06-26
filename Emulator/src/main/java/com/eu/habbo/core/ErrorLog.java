package com.eu.habbo.core;

import com.eu.habbo.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class ErrorLog implements DatabaseLoggable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ErrorLog.class);
    private static final String QUERY = "INSERT INTO emulator_errors (timestamp, version, build_hash, type, stacktrace) VALUES (?, ?, ?, ?, ?)";

    public final String version;
    public final String buildHash;

    public final int timeStamp;
    public final String type;
    public final String stackTrace;

    public ErrorLog(String type, Throwable e) {
        this.version = Emulator.version;
        this.buildHash = Emulator.version;

        this.timeStamp = Emulator.getIntUnixTimestamp();
        this.type = type;

        var sw = new StringWriter();
        var pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        this.stackTrace = sw.toString();

        try {
            pw.close();
            sw.close();
        } catch (IOException e1) {
            LOGGER.error("Exception caught", e1);
        }
    }

    public ErrorLog(String type, String message) {
        this.version = Emulator.version;
        this.buildHash = Emulator.build;

        this.timeStamp = Emulator.getIntUnixTimestamp();
        this.type = type;
        this.stackTrace = message;
    }

    @Override
    public String getQuery() {
        return QUERY;
    }

    @Override
    public void log(PreparedStatement statement) throws SQLException {
        statement.setInt(1, this.timeStamp);
        statement.setString(2, this.version);
        statement.setString(3, this.buildHash);
        statement.setString(4, this.type);
        statement.setString(5, this.stackTrace);
        statement.addBatch();
    }
}
