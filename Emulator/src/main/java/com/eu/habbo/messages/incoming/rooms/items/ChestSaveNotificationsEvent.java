package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.items.ChestSettingsUpdatedComposer;

/**
 * Save a chest's notification switches (AIR {@code ChestNotificationSettingsUI}): {@code int chestId,
 * int notifyMode, bool chestFull, bool donation, bool someoneWithdraws, bool chestEmpty,
 * bool wiredTransaction}. The owner's choice; a save answers {@code ChestSettingsUpdated} with
 * {@code isNotificationPreferences} set.
 */
public class ChestSaveNotificationsEvent extends MessageHandler {
    /** AIR offers two modes: 0 always, 1 only while in the room. */
    static final int MAX_NOTIFY_MODE = 1;

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

        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        int mode = this.packet.readInt();
        boolean full = this.packet.readBoolean();
        boolean donation = this.packet.readBoolean();
        boolean withdraw = this.packet.readBoolean();
        boolean empty = this.packet.readBoolean();
        boolean wired = this.packet.readBoolean();

        HabboItem item = room.getHabboItem(itemId);
        if (!(item instanceof InteractionWiredChest chest)) return;
        if (habbo.getHabboInfo().getId() != chest.getUserId()) return;
        if (mode < 0 || mode > MAX_NOTIFY_MODE) return;

        chest.getContents().setNotifications(full, donation, withdraw, empty, wired, mode);
        chest.persistContents();
        chest.publishSettings(room);

        this.client.sendResponse(new ChestSettingsUpdatedComposer(chest.getId(), true));
    }
}
