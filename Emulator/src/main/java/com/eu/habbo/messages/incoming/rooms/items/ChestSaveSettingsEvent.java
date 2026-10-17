package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestStorage;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.items.ChestSettingsUpdatedComposer;

/**
 * Save a chest's settings (AIR {@code ChestSettingsUI}): {@code int chestId, string name,
 * string description, bool everyoneCanOpen, bool everyoneCanDonate, int stateMode, int previewMode,
 * int previewAmount, bool wiredEnabled}.
 *
 * <p>The owner's decision only. The Wired upgrade is this save with {@code wiredEnabled} set: it is
 * one way (a chest that answers wired keeps answering), it leaves the chest locked, and a starter
 * chest refuses it. A save that goes through answers {@code ChestSettingsUpdated}; the settings reach
 * the room with the furni.
 */
public class ChestSaveSettingsEvent extends MessageHandler {
    static final int MAX_NAME = 30;
    static final int MAX_DESCRIPTION = 200;
    static final int MAX_STATE_MODE = 3;

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
        int stateMode = this.packet.readInt();
        int previewMode = this.packet.readInt();
        int previewAmount = this.packet.readInt();
        boolean wiredEnabled = this.packet.readBoolean();

        HabboItem item = room.getHabboItem(itemId);
        if (!(item instanceof InteractionWiredChest chest)) return;
        if (habbo.getHabboInfo().getId() != chest.getUserId()) return;

        ChestStorage c = chest.getContents();
        if (c.isWiredEnabled() && !wiredEnabled) return;
        if (wiredEnabled && !c.isWiredEnabled() && chest.isStarterChest()) return;
        if (stateMode < 0 || stateMode > MAX_STATE_MODE) return;

        synchronized (c) {
            c.setName(bound(name, MAX_NAME));
            c.setDescription(bound(description, MAX_DESCRIPTION));
            c.setAccessOpen(accessOpen);
            c.setAccessDonate(accessDonate);
            c.setAppearanceState(stateMode);
            // A coin chest has no preview; the client sends whatever its hidden controls held.
            if (chest.chestKind() == ChestStorage.KIND_FURNI) c.setPreview(previewMode, previewAmount);
            if (wiredEnabled) c.enableWired();
        }
        chest.persistContents();
        chest.publishSettings(room);

        this.client.sendResponse(new ChestSettingsUpdatedComposer(chest.getId(), false));
    }

    private static String bound(String value, int max) {
        if (value == null) return "";
        return value.length() > max ? value.substring(0, max) : value;
    }
}
