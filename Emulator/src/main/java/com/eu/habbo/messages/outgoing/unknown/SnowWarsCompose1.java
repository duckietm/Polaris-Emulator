package com.eu.habbo.messages.outgoing.unknown;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;

public class SnowWarsCompose1 extends MessageComposer {
    private final int header;

    public SnowWarsCompose1(int header) {
        this.header = header;
    }

    //:test 1604 i:1 s:a i:10 i:2 i:3 i:4 s:1 i:3 i:10 i:1 s:Admin s:%look% s:M i:0 i:0 i:0 i:0
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(this.header);
        this.response.appendInt(1);
        this.response.appendString("SnowStorm level " + 9);
        this.response.appendInt(0); //Stage ID
        this.response.appendInt(9);
        this.response.appendInt(4);
        this.response.appendInt(8);
        this.response.appendString("Admin");
        this.response.appendInt(14);
        this.response.appendInt(2);
        this.response.appendInt(1);
        this.response.appendString("Admin");
        this.response.appendString("ca-1807-64.lg-275-78.hd-3093-1.hr-802-42.ch-3110-65-62.fa-1211-62");
        this.response.appendString("m");
        this.response.appendInt(1);
        this.response.appendInt(0);
        this.response.appendInt(0);
        this.response.appendInt(10);
        this.response.appendInt(2);
        this.response.appendString("Droppy");
        this.response.appendString("ca-1807-64.lg-275-78.hd-3093-1.hr-802-42.ch-3110-65-62.fa-1211-62");
        this.response.appendString("m");
        this.response.appendInt(2);
        this.response.appendInt(0);
        this.response.appendInt(0);
        this.response.appendInt(10);
        return this.response;
    }
}
