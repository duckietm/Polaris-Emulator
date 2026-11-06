package polaris.messages.incoming.polls;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.polls.Poll;
import polaris.hotel.rooms.Room;
import polaris.messages.incoming.MessageHandler;

public class CancelPollEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CancelPollEvent.class);

    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        int pollId = this.packet.readInt();

        Room room = this.client.getUser().getUserInfo().getCurrentRoom();
        if (room == null || room.getPollId() != pollId) {
            return;
        }

        Poll poll = Emulator.getGameEnvironment().getPollManager().getPoll(pollId);

        if (poll != null) {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO polls_answers (poll_id, user_id, question_id, answer) VALUES (?, ?, ?, ?)")) {
                statement.setInt(1, pollId);
                statement.setInt(2, this.client.getUser().getUserInfo().getId());
                statement.setInt(3, 0);
                statement.setString(4, "");
                statement.execute();
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }
    }
}
