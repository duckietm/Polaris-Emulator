package com.eu.habbo.messages.outgoing.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class UserDataComposer extends MessageComposer {
    private final Habbo habbo;

    public UserDataComposer(Habbo habbo) {
        this.habbo = habbo;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.UserDataComposer);

        this.response.appendInt(this.habbo.getHabboInfo().getId());
        this.response.appendString(this.habbo.getHabboInfo().getUsername());
        this.response.appendString(this.habbo.getHabboInfo().getLook());
        this.response.appendString(this.habbo.getHabboInfo().getGender().name().toUpperCase());
        this.response.appendString(this.habbo.getHabboInfo().getMotto());
        this.response.appendString(this.habbo.getHabboInfo().getUsername());
        this.response.appendBoolean(false);
        this.response.appendInt(this.habbo.getHabboStats().respectPointsReceived);
        this.response.appendInt(this.habbo.getHabboStats().respectPointsToGive);
        this.response.appendInt(this.habbo.getHabboStats().petRespectPointsToGive);
        this.response.appendBoolean(false);
        this.response.appendString("01-01-1970 00:00:00");
        this.response.appendBoolean(this.habbo.getHabboStats().allowNameChange); // can change name.
        this.response.appendBoolean(this.habbo.getHabboStats().safetyLocked); // accountSafetyLocked

        // Official class_1757.parse reads two more optional blocks when bytes remain:
        // (accountTradeLocked, nameColor) and (respectReplenishesLeft, maxRespectPerDay).
        this.response.appendBoolean(!this.habbo.getHabboStats().allowTrade());
        this.response.appendString("");
        this.response.appendInt(this.habbo.getHabboStats().respectReplenishesLeft);
        this.response.appendInt(Emulator.getConfig().getInt("hotel.daily.respect"));

        return this.response;
    }

    public Habbo getHabbo() {
        return habbo;
    }
}
