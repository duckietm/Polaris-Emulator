package com.eu.habbo.habbohotel.polls;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class PollManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PollManager.class);

    private final Map<Integer, Poll> activePolls = new HashMap<>();

    public PollManager() {
        this.loadPolls();
    }

    public static boolean donePoll(Habbo habbo, int pollId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT NULL FROM polls_answers WHERE poll_id = ? AND user_id = ? LIMIT 1")) {
            statement.setInt(1, pollId);
            statement.setInt(2, habbo.getHabboInfo().getId());
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
        synchronized (this.activePolls) {
            this.activePolls.clear();

            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
                try (Statement statement = connection.createStatement()) {
                    try (ResultSet set = statement.executeQuery("SELECT * FROM polls")) {
                        while (set.next()) {
                            this.activePolls.put(set.getInt("id"), new Poll(set));
                        }
                    }

                    try (ResultSet set = statement.executeQuery("SELECT * FROM polls_questions ORDER BY parent_id, `order` ASC")) {
                        while (set.next()) {
                            Poll poll = this.getPoll(set.getInt("poll_id"));

                            if (poll != null) {
                                PollQuestion question = new PollQuestion(set);

                                if (set.getInt("parent_id") <= 0) {
                                    poll.addQuestion(question);
                                } else {
                                    PollQuestion parentQuestion = poll.getQuestion(set.getInt("parent_id"));

                                    if (parentQuestion != null) {
                                        parentQuestion.addSubQuestion(question);
                                    }
                                }

                                poll.lastQuestionId = question.id;
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }
    }

    public Poll getPoll(int pollId) {
        return this.activePolls.get(pollId);
    }
}
