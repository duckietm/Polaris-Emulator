package com.eu.habbo.messages.outgoing.unknown;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;

public class SnowWarsOnStageStartComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(889);
        this.response.appendInt(0);
        this.response.appendString("snowwar_arena_0");
        this.response.appendInt(0);
        this.response.appendInt(2); //Count

        this.response.appendInt(5);
        this.response.appendInt(1);
        this.response.appendInt(64000);
        this.response.appendInt(64000);
        this.response.appendInt(20);
        this.response.appendInt(24);
        this.response.appendInt(1);
        this.response.appendInt(100);
        this.response.appendInt(4);
        this.response.appendInt(0);
        this.response.appendInt(0);
        this.response.appendInt(0);
        this.response.appendInt(20);
        this.response.appendInt(24);
        this.response.appendInt(64000);
        this.response.appendInt(64000);
        this.response.appendInt(0);
        this.response.appendInt(1);
        this.response.appendInt(1);
        this.response.appendString("Admin");
        this.response.appendString("Motto");
        this.response.appendString("ca-1807-64.lg-275-78.hd-3093-1.hr-802-42.ch-3110-65-62.fa-1211-62");
        this.response.appendString("m");

        this.response.appendInt(5);
        this.response.appendInt(2);
        this.response.appendInt(64000);
        this.response.appendInt(64000);
        this.response.appendInt(20);
        this.response.appendInt(24);
        this.response.appendInt(1);
        this.response.appendInt(100);
        this.response.appendInt(4);
        this.response.appendInt(0);
        this.response.appendInt(0);
        this.response.appendInt(0);
        this.response.appendInt(20);
        this.response.appendInt(24);
        this.response.appendInt(64000);
        this.response.appendInt(64000);
        this.response.appendInt(0);
        this.response.appendInt(2);
        this.response.appendInt(2);
        this.response.appendString("Admin");
        this.response.appendString("Motto");
        this.response.appendString("ca-1807-64.lg-275-78.hd-3093-1.hr-802-42.ch-3110-65-62.fa-1211-62");
        this.response.appendString("m");

        return this.response;
    }
}
