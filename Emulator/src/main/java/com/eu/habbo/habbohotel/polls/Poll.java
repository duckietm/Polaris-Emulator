package com.eu.habbo.habbohotel.polls;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;

public class Poll {

    public final int id;


    public final String title;


    public final String thanksMessage;


    public final String badgeReward;

    public int lastQuestionId;

    private ArrayList<PollQuestion> questions;

    public Poll(ResultSet set) throws SQLException {
        this.id = set.getInt("id");
        this.title = set.getString("title");
        this.thanksMessage = set.getString("thanks_message");
        this.badgeReward = set.getString("reward_badge");
        this.questions = new ArrayList<>();
    }

    public ArrayList<PollQuestion> getQuestions() {
        return this.questions;
    }

    public PollQuestion getQuestion(int id) {
        for (PollQuestion q : this.questions) {
            if (q.id == id) {
                return q;
            }
        }

        return null;
    }

    /** Whether this poll asks the question, as a question of its own or a sub-question of one. */
    public boolean hasQuestion(int id) {
        for (PollQuestion q : this.questions) {
            if (q.id == id || q.hasSubQuestion(id)) {
                return true;
            }
        }

        return false;
    }

    public void addQuestion(PollQuestion question) {
        this.questions.add(question);

        Collections.sort(this.questions);
    }
}
