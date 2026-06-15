package com.eu.habbo.messages.incoming.polls;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.polls.Poll;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboBadge;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.users.AddUserBadgeComposer;
import com.eu.habbo.messages.outgoing.wired.WiredRewardAlertComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class AnswerPollEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnswerPollEvent.class);

    @Override
    public void handle() throws Exception {
        int pollId = this.packet.readInt();
        int questionId = this.packet.readInt();
        int count = this.packet.readInt();
        String answers = this.packet.readString();
        
        StringBuilder answer = new StringBuilder();
        for (int i = 0; i < count; i++) {
            answer.append(":").append(answers);
        }

        if(answer.length() <= 0) return;

        if (pollId == 0 && questionId <= 0) {
            Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
            if (room != null) {
                room.handleWordQuiz(this.client.getHabbo(), answer.toString());
            }
            return;
        }

        answer = new StringBuilder(answer.substring(1));

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null || room.getPollId() != pollId) {
            return;
        }

        Poll poll = Emulator.getGameEnvironment().getPollManager().getPoll(pollId);

        if (poll != null) {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("INSERT INTO polls_answers(poll_id, user_id, question_id, answer) VALUES(?, ?, ?, ?) ON DUPLICATE KEY UPDATE answer=VALUES(answer)")) {
                statement.setInt(1, pollId);
                statement.setInt(2, this.client.getHabbo().getHabboInfo().getId());
                statement.setInt(3, questionId);
                statement.setString(4, answer.toString());
                statement.execute();
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }

            if (poll.lastQuestionId == questionId) {
                if (poll.badgeReward.length() > 0) {
                    if (!this.client.getHabbo().getInventory().getBadgesComponent().hasBadge(poll.badgeReward)) {
                        HabboBadge badge = new HabboBadge(0, poll.badgeReward, 0, this.client.getHabbo());
                        Emulator.getThreading().run(badge);
                        this.client.getHabbo().getInventory().getBadgesComponent().addBadge(badge);
                        this.client.sendResponse(new AddUserBadgeComposer(badge));
                        this.client.sendResponse(new WiredRewardAlertComposer(WiredRewardAlertComposer.REWARD_RECEIVED_BADGE));
                    } else {
                        this.client.sendResponse(new WiredRewardAlertComposer(WiredRewardAlertComposer.REWARD_ALREADY_RECEIVED));
                    }
                }
            }
        }
    }
}
