package com.eu.habbo.messages.incoming.catalog;

import com.eu.habbo.habbohotel.rooms.BuildersClubRecolorService;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;

public class BuildersClubRecolorFurniEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        int itemId = this.packet.readInt();
        int colorIndex = this.packet.readInt();
        int scope = this.packet.readInt();

        BuildersClubRecolorService.Result result =
                BuildersClubRecolorService.recolor(this.client.getHabbo(), itemId, colorIndex, scope);

        if (result == BuildersClubRecolorService.Result.DONE || result == BuildersClubRecolorService.Result.TOO_FAST) {
            return;
        }

        this.client.sendResponse(new BubbleAlertComposer(
                BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key,
                "builder.recolor.error." + result.name().toLowerCase()));
    }
}
