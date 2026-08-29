package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.items.ChestDataComposer;

/**
 * Make a chest answer wired: {@code int itemId}.
 *
 * <p>A chest starts as storage. Its owner decides to make it part of the room's machinery, and from
 * then on wired effects and conditions can reach it. There is no message to undo this, deliberately:
 * a room built around a chest would break the moment somebody switched it off, and the client asks
 * for confirmation precisely because it is permanent.
 *
 * <p>The owner's decision, not the room's — someone with rights can use a chest, not repurpose it.
 */
public class ChestEnableWiredEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null) return;

        HabboItem item = room.getHabboItem(this.packet.readInt());
        if (!(item instanceof InteractionWiredChest chest)) return;
        if (habbo.getHabboInfo().getId() != chest.getUserId()) return;

        // A starter chest is a taste of the feature, not a building block for a room.
        if (chest.isStarterChest()) return;
        if (chest.answersWired()) return;

        chest.getContents().enableWired();
        chest.persistContents(room);

        this.client.sendResponse(new ChestDataComposer(chest, habbo));
    }
}
