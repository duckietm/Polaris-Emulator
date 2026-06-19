package com.eu.habbo.habbohotel.polls;

import com.eu.habbo.messages.ISerialize;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class PollQuestion implements ISerialize, Comparable<PollQuestion> {

    public final int id;


    public final int parentId;


    public final int type;


    public final String question;


    public final Map<Integer, String[]> options;


    public final int minSelections;


    public final int order;

    private ArrayList<PollQuestion> subQuestions;

    public PollQuestion(ResultSet set) throws SQLException {
        this.id = set.getInt("id");
        this.parentId = set.getInt("parent_id");
        this.type = set.getInt("type");
        this.question = set.getString("question");
        this.minSelections = set.getInt("min_selections");
        this.order = set.getInt("order");

        this.options = new HashMap<>();
        this.subQuestions = new ArrayList<>();

        String opts = set.getString("options");

        if (this.type == 1 || this.type == 2) {
            for (int i = 0; i < opts.split(";").length; i++) {
                this.options.put(i, new String[]{opts.split(";")[i].split(":")[0], opts.split(";")[i].split(":")[1]});
            }
        }
    }

    public void addSubQuestion(PollQuestion pollQuestion) {
        this.subQuestions.add(pollQuestion);
    }

    @Override
    public void serialize(ServerMessage message) {
        message.appendInt(this.id);
        message.appendInt(this.order);
        message.appendInt(this.type);
        message.appendString(this.question);
        message.appendInt(this.minSelections);
        message.appendInt(0);
        message.appendInt(this.options.size());

        if (this.type == 1 || this.type == 2) {
            for (Map.Entry<Integer, String[]> set : this.options.entrySet()) {
                message.appendString(set.getValue()[0]);
                message.appendString(set.getValue()[1]);
                message.appendInt(set.getKey());
            }
        }

        if (this.parentId <= 0) {
            Collections.sort(this.subQuestions);
            message.appendInt(this.subQuestions.size());

            for (PollQuestion q : this.subQuestions) {
                q.serialize(message);
            }
        }
    }

    @Override
    public int compareTo(PollQuestion o) {
        return this.order - o.order;
    }
}
