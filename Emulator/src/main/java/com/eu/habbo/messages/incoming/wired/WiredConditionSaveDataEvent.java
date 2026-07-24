package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.generic.alerts.UpdateFailedComposer;
import com.eu.habbo.messages.outgoing.wired.WiredSavedComposer;

public class WiredConditionSaveDataEvent extends MessageHandler {
    private static final WiredConditionSaveAdapter SAVE_ADAPTER = new WiredConditionSaveAdapter();

    @Override
    public void handle() throws Exception {
        int itemId = this.packet.readInt();

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room != null) {
            if (room.canModifyWired(this.client.getHabbo())) {
                InteractionWiredCondition condition = room.getRoomSpecialTypes().getCondition(itemId);

                if (condition != null) {
                    boolean saved;
                    try {
                        saved = SAVE_ADAPTER.save(
                                condition, () -> InteractionWired.readSettings(this.packet, false), () -> this.packet);
                    } catch (IllegalArgumentException exception) {
                        this.client.sendResponse(new UpdateFailedComposer("Invalid wired condition settings"));
                        return;
                    }

                    if (!saved) {
                        this.client.sendResponse(
                                new UpdateFailedComposer("There was an error while saving that condition"));
                        return;
                    }

                    this.client.sendResponse(new WiredSavedComposer());
                    condition.needsUpdate(true);
                    Emulator.getThreading().run(condition);
                    WiredManager.invalidateRoom(room);
                }
            }
        }
    }
}
