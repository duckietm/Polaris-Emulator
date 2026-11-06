package polaris.hotel.polls;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.users.User;

public class PollManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PollManager.class);

    // Loaded into a new map and swapped in: a reload used to clear the map poll packets were reading.
    private volatile Map<Integer, Poll> activePolls = new HashMap<>();

    public PollManager() {
        this.loadPolls();
    }

    public static boolean donePoll(User user, int pollId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT NULL FROM polls_answers WHERE poll_id = ? AND user_id = ? LIMIT 1")) {
            statement.setInt(1, pollId);
            statement.setInt(2, user.getUserInfo().getId());
            try (ResultSet set = statement.executeQuery()) {
                if (set.isBeforeFirst()) {
                    return true;
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
        return false;
    }

    public void loadPolls() {
        Map<Integer, Poll> loaded = new HashMap<>();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            try (Statement statement = connection.createStatement()) {
                try (ResultSet set = statement.executeQuery("SELECT * FROM polls")) {
                    while (set.next()) {
                        loaded.put(set.getInt("id"), new Poll(set));
                    }
                }

                try (ResultSet set =
                        statement.executeQuery("SELECT * FROM polls_questions ORDER BY parent_id, `order` ASC")) {
                    while (set.next()) {
                        Poll poll = loaded.get(set.getInt("poll_id"));

                        if (poll != null) {
                            PollQuestion question = new PollQuestion(set);

                            if (set.getInt("parent_id") <= 0) {
                                poll.addQuestion(question);
                                // Sub-questions only show for some answers, so the reward follows the last main
                                // one.
                                poll.lastQuestionId = question.id;
                            } else {
                                PollQuestion parentQuestion = poll.getQuestion(set.getInt("parent_id"));

                                if (parentQuestion != null) {
                                    parentQuestion.addSubQuestion(question);
                                }
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
            return;
        }
        this.activePolls = loaded;
    }

    public Poll getPoll(int pollId) {
        return this.activePolls.get(pollId);
    }
}
