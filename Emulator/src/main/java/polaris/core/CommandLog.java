package polaris.core;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import polaris.Emulator;
import polaris.hotel.commands.Command;

public class CommandLog implements DatabaseLoggable {

    private static final String INSERT_QUERY =
            "INSERT INTO commandlogs (`user_id`, `timestamp`, `command`, `params`, `succes`) VALUES (?, ?, ?, ?, ?)";

    private final int userId;
    private final int timestamp = Emulator.getIntUnixTimestamp();
    private final Command command;
    private final String params;
    private final boolean succes;

    public CommandLog(int userId, Command command, String params, boolean succes) {
        this.userId = userId;
        this.command = command;
        this.params = params;
        this.succes = succes;
    }

    @Override
    public String getQuery() {
        return CommandLog.INSERT_QUERY;
    }

    @Override
    public void log(PreparedStatement statement) throws SQLException {
        statement.setInt(1, this.userId);
        statement.setInt(2, this.timestamp);
        statement.setString(3, this.command.getClass().getSimpleName());
        statement.setString(4, this.params);
        statement.setString(5, this.succes ? "yes" : "no");
        statement.addBatch();
    }
}
