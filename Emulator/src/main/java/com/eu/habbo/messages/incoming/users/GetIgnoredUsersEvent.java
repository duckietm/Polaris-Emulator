package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.unknown.IgnoredUsersComposer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class GetIgnoredUsersEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        String requestedUsername = this.packet.readString();
        String currentUsername = this.client.getHabbo().getHabboInfo().getUsername();

        if (!currentUsername.equalsIgnoreCase(requestedUsername)) {
            this.client.sendResponse(new IgnoredUsersComposer(List.of()));
            return;
        }

        List<String> ignoredUsernames = new ArrayList<>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT target.username " +
                             "FROM users_ignored ignored " +
                             "INNER JOIN users target ON target.id = ignored.target_id " +
                             "WHERE ignored.user_id = ? " +
                             "ORDER BY target.username")) {
            statement.setInt(1, this.client.getHabbo().getHabboInfo().getId());

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    ignoredUsernames.add(result.getString("username"));
                }
            }
        }

        this.client.sendResponse(new IgnoredUsersComposer(ignoredUsernames));
    }
}
