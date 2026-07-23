package com.eu.habbo.messages.outgoing.unknown;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;

public class SnowWarsUserEnteredArenaComposer extends MessageComposer {
    private final int type;

    public SnowWarsUserEnteredArenaComposer(int type) {
        this.type = type;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(3425);

        if (this.type == 1) {
            this.response.appendInt(1); //userId
            this.response.appendString("Admin");
            this.response.appendString("ca-1807-64.lg-275-78.hd-3093-1.hr-802-42.ch-3110-65-62.fa-1211-62");
            this.response.appendString("m");
            this.response.appendInt(1); //team
        } else {
            this.response.appendInt(0); //userId
            this.response.appendString("Droppy");
            this.response.appendString("ca-1807-64.lg-275-78.hd-3093-1.hr-802-42.ch-3110-65-62.fa-1211-62");
            this.response.appendString("m");
            this.response.appendInt(2); //team
        }
        return this.response;
    }

    public int getType() {
        return type;
    }
}
