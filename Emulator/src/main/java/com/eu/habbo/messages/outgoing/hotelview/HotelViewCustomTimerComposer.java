package com.eu.habbo.messages.outgoing.hotelview;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.UnsupportedOutgoing;

public class HotelViewCustomTimerComposer extends MessageComposer {
    private final String name;
    private final int seconds;

    public HotelViewCustomTimerComposer(String name, int seconds) {
        this.name = name;
        this.seconds = seconds;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(UnsupportedOutgoing.HotelViewCustomTimerComposer);
        this.response.appendString(this.name); // Send by the client.
        this.response.appendInt(this.seconds);
        return this.response;
    }

    public String getName() {
        return name;
    }

    public int getSeconds() {
        return seconds;
    }
}
