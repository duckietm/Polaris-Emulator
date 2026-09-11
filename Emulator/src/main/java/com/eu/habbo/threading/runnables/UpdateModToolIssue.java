package com.eu.habbo.threading.runnables;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.modtool.ModToolIssue;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpdateModToolIssue implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(UpdateModToolIssue.class);

    private final ModToolIssue issue;

    public UpdateModToolIssue(ModToolIssue issue) {
        this.issue = issue;
    }

    @Override
    public void run() {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE support_tickets SET state = ?, type = ?, mod_id = ?, category = ?, closed_timestamp = ?, sanctioned = ?, sanction_auto = ?, appeal_state = ?, appeal_timestamp = ?, appeal_resolved_timestamp = ? WHERE id = ?")) {
            statement.setInt(1, this.issue.state.getState());
            statement.setInt(2, this.issue.type.getType());
            statement.setInt(3, this.issue.modId);
            statement.setInt(4, this.issue.category);
            statement.setInt(5, this.issue.closedTimestamp);
            statement.setInt(6, this.issue.sanctioned ? 1 : 0);
            statement.setInt(7, this.issue.sanctionGivenByAutoModeration ? 1 : 0);
            statement.setInt(8, this.issue.appealState);
            statement.setInt(9, this.issue.appealTimestamp);
            statement.setInt(10, this.issue.appealResolvedTimestamp);
            statement.setInt(11, this.issue.id);
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }
}
