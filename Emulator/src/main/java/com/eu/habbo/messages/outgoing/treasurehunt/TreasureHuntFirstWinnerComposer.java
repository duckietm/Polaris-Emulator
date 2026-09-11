package com.eu.habbo.messages.outgoing.treasurehunt;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * AIR 13 TreasureHuntFirstWinner: the {@code TreasureHuntWinnerInfo} structure
 * (hunt code, user id, user name, figure, gender) the notification component
 * turns into the "first winner" bubble with the winner's head.
 */
public class TreasureHuntFirstWinnerComposer extends MessageComposer {
    private final String huntCode;
    private final int userId;
    private final String userName;
    private final String figure;
    private final String gender;

    public TreasureHuntFirstWinnerComposer(String huntCode, int userId, String userName, String figure, String gender) {
        this.huntCode = huntCode;
        this.userId = userId;
        this.userName = userName;
        this.figure = figure;
        this.gender = gender;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.TreasureHuntFirstWinnerComposer);
        this.response.appendString(this.huntCode);
        this.response.appendInt(this.userId);
        this.response.appendString(this.userName);
        this.response.appendString(this.figure);
        this.response.appendString(this.gender);
        return this.response;
    }
}
