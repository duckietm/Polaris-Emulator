package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.items.recolorable.RecolorableFurniService;
import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;

/** Official furni recolour (AIR 2852): the furni, the scope (0-3) and the colour as r, g, b. */
public class RecolorFurniEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 250;
    }

    @Override
    public void handle() throws Exception {
        int furniId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        int scope = this.packet.readInt();
        int red = this.packet.readInt();
        int green = this.packet.readInt();
        int blue = this.packet.readInt();

        RecolorableFurniService.Result result =
                RecolorableFurniService.recolor(this.client.getHabbo(), furniId, scope, red, green, blue);

        if (result == RecolorableFurniService.Result.DONE || result == RecolorableFurniService.Result.TOO_FAST) {
            return;
        }

        this.client.sendResponse(new BubbleAlertComposer(
                BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key,
                "builder.recolor.error." + result.name().toLowerCase()));
    }
}
