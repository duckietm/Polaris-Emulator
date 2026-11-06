package polaris.messages.incoming.rooms.items;

import polaris.hotel.items.recolorable.RecolorableFurniService;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.generic.alerts.BubbleAlertComposer;
import polaris.messages.outgoing.generic.alerts.BubbleAlertKeys;

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
                RecolorableFurniService.recolor(this.client.getUser(), furniId, scope, red, green, blue);

        if (result == RecolorableFurniService.Result.DONE || result == RecolorableFurniService.Result.TOO_FAST) {
            return;
        }

        this.client.sendResponse(new BubbleAlertComposer(
                BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key,
                "builder.recolor.error." + result.name().toLowerCase()));
    }
}
