package com.eu.habbo.messages.outgoing.rooms.users;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.UserCustomizationData;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class RoomUserDataComposer extends MessageComposer {
    private final Habbo habbo;

    public RoomUserDataComposer(Habbo habbo) {
        this.habbo = habbo;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomUserDataComposer);
        this.response.appendInt(this.habbo.getRoomUnit() == null ? -1 : this.habbo.getRoomUnit().getId());
        this.response.appendString(this.habbo.getHabboInfo().getLook());
        this.response.appendString(this.habbo.getHabboInfo().getGender().name() + "");
        this.response.appendString(this.habbo.getHabboInfo().getMotto());
        this.response.appendInt(this.habbo.getHabboStats().getAchievementScore());
        this.response.appendInt(this.habbo.getHabboInfo().getInfostandBg());
        this.response.appendInt(this.habbo.getHabboInfo().getInfostandStand());
        this.response.appendInt(this.habbo.getHabboInfo().getInfostandOverlay());
        this.response.appendInt(this.habbo.getHabboInfo().getInfostandCardBg());
        UserCustomizationData customizationData = UserCustomizationData.fromHabbo(this.habbo);
        this.response.appendString(customizationData.nickIcon);
        this.response.appendString(customizationData.prefixText);
        this.response.appendString(customizationData.prefixColor);
        this.response.appendString(customizationData.prefixIcon);
        this.response.appendString(customizationData.prefixEffect);
        this.response.appendString(customizationData.prefixFont);
        this.response.appendString(customizationData.displayOrder);
        this.response.appendInt(this.habbo.getHabboInfo().getInfostandBorder());
        return this.response;
    }

    public Habbo getHabbo() {
        return habbo;
    }
}
