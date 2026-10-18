package com.eu.habbo.messages.outgoing.unknown;

import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class WelcomeGiftComposer extends MessageComposer {
    private final String email;
    private final boolean unknownB1;
    private final boolean unknownB2;
    private final int furniId;
    private final boolean unknownB3;

    public WelcomeGiftComposer(String email, boolean unknownB1, boolean unknownB2, int furniId, boolean unknownB3) {
        this.email = email;
        this.unknownB1 = unknownB1;
        this.unknownB2 = unknownB2;
        this.furniId = furniId;
        this.unknownB3 = unknownB3;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WelcomeGiftComposer);
        this.response.appendString(this.email);
        this.response.appendBoolean(this.unknownB1);
        this.response.appendBoolean(this.unknownB2);
        this.response.appendInt(BuildersClubItemIds.toWire(this.furniId));
        this.response.appendBoolean(this.unknownB3);
        return this.response;
    }

    public String getEmail() {
        return email;
    }

    public boolean isUnknownB1() {
        return unknownB1;
    }

    public boolean isUnknownB2() {
        return unknownB2;
    }

    public int getFurniId() {
        return furniId;
    }

    public boolean isUnknownB3() {
        return unknownB3;
    }
}
