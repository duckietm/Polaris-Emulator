package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestStorage;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.items.ChestDataComposer;

/**
 * Saves a wired chest's settings (room-rights only): {@code int itemId, string name, string description,
 * bool accessOpen, bool accessDonate, int appearanceState}.
 *
 * <p>The lock, the auto-lock and the capacity ceiling are not here: they live in the chest
 * window itself and save through {@link ChestSaveOptionsEvent} the moment they are touched,
 * so closing a chest is one click rather than a trip through a settings dialog.
 */
public class ChestSaveSettingsEvent extends MessageHandler {
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

        int itemId = this.packet.readInt();
        String name = this.packet.readString();
        String description = this.packet.readString();
        boolean accessOpen = this.packet.readBoolean();
        boolean accessDonate = this.packet.readBoolean();
        int appearanceState = this.packet.readInt();

        HabboItem item = room.getHabboItem(itemId);
        if (!(item instanceof InteractionWiredChest chest)) return;
        if (!room.hasRights(habbo)) return;

        ChestStorage c = chest.getContents();
        c.setName(bound(name, 60));
        c.setDescription(bound(description, 255));
        c.setAccessOpen(accessOpen);
        c.setAccessDonate(accessDonate);
        c.setAppearanceState(appearanceState);
        chest.persistContents(room);

        this.client.sendResponse(new ChestDataComposer(chest, this.client.getHabbo()));
    }

    private static String bound(String value, int max) {
        if (value == null) return "";
        return value.length() > max ? value.substring(0, max) : value;
    }
}
