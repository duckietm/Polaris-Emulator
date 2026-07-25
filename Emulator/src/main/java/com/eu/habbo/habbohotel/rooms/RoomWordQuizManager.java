package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.polls.infobus.SimplePollAnswerComposer;
import com.eu.habbo.messages.outgoing.polls.infobus.SimplePollStartComposer;
import java.util.List;

/**
 * Manages word quizzes/polls within a room.
 */
public class RoomWordQuizManager {
    private final Room room;

    public RoomWordQuizManager(Room room) {
        this.room = room;
    }

    /**
     * Handles a user's quiz answer.
     */
    public void handleWordQuiz(Habbo habbo, String answer) {
        synchronized (this.room.userVotes) {
            if (!this.room.wordQuiz.isEmpty() && !this.hasVotedInWordQuiz(habbo)) {
                answer = answer.replace(":", "");

                if (answer.equals("0")) {
                    this.room.noVotes++;
                } else if (answer.equals("1")) {
                    this.room.yesVotes++;
                }

                this.room.sendComposer(new SimplePollAnswerComposer(
                                habbo.getHabboInfo().getId(), answer, this.room.noVotes, this.room.yesVotes)
                        .compose());
                this.room.userVotes.add(habbo.getHabboInfo().getId());
            }
        }
    }

    /**
     * Starts a word quiz.
     */
    public void startWordQuiz(String question, int duration) {
        if (!this.hasActiveWordQuiz()) {
            this.room.wordQuiz = question;
            this.room.noVotes = 0;
            this.room.yesVotes = 0;
            this.room.userVotes.clear();
            this.room.wordQuizEnd = Emulator.getIntUnixTimestamp() + (duration / 1000);
            this.room.sendComposer(new SimplePollStartComposer(duration, question).compose());
        }
    }

    /**
     * Checks if there is an active word quiz.
     */
    public boolean hasActiveWordQuiz() {
        return Emulator.getIntUnixTimestamp() < this.room.wordQuizEnd;
    }

    /**
     * Checks if a user has voted in the current quiz.
     */
    public boolean hasVotedInWordQuiz(Habbo habbo) {
        return this.room.userVotes.contains(habbo.getHabboInfo().getId());
    }

    /**
     * Resets the quiz state.
     */
    public void reset() {
        this.room.wordQuiz = "";
        this.room.yesVotes = 0;
        this.room.noVotes = 0;
        this.room.userVotes.clear();
    }

    // Getters and setters for backward compatibility
    public String getWordQuiz() {
        return this.room.wordQuiz;
    }

    public void setWordQuiz(String wordQuiz) {
        this.room.wordQuiz = wordQuiz;
    }

    public int getNoVotes() {
        return this.room.noVotes;
    }

    public void setNoVotes(int noVotes) {
        this.room.noVotes = noVotes;
    }

    public int getYesVotes() {
        return this.room.yesVotes;
    }

    public void setYesVotes(int yesVotes) {
        this.room.yesVotes = yesVotes;
    }

    public int getWordQuizEnd() {
        return this.room.wordQuizEnd;
    }

    public void setWordQuizEnd(int wordQuizEnd) {
        this.room.wordQuizEnd = wordQuizEnd;
    }

    public List<Integer> getUserVotes() {
        return this.room.userVotes;
    }
}
