package com.eu.habbo.messages.outgoing.wheel;

import com.eu.habbo.habbohotel.wheel.WheelRecentWin;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.List;

// "Latest winners" list: username + look (for the headshot) + prize label.
public class WheelRecentWinsComposer extends MessageComposer {
    private final List<WheelRecentWin> wins;

    public WheelRecentWinsComposer(List<WheelRecentWin> wins) {
        this.wins = wins;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WheelRecentWinsComposer);
        this.response.appendInt(this.wins.size());
        for (WheelRecentWin win : this.wins) {
            this.response.appendString(win.username);
            this.response.appendString(win.look);
            this.response.appendString(win.prizeLabel);
        }
        return this.response;
    }
}
