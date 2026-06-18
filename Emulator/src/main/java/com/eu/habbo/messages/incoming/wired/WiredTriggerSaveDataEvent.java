package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.generic.alerts.UpdateFailedComposer;
import com.eu.habbo.messages.outgoing.wired.WiredSavedComposer;

public class WiredTriggerSaveDataEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int itemId = this.packet.readInt();

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room != null) {
            if (room.canModifyWired(this.client.getHabbo())) {
                InteractionWiredTrigger trigger = room.getRoomSpecialTypes().getTrigger(itemId);

                if (trigger != null) {
                    WiredSettings settings;
                    try {
                        settings = InteractionWired.readSettings(this.packet, false);
                    } catch (IllegalArgumentException e) {
                        this.client.sendResponse(new UpdateFailedComposer("Invalid wired trigger settings"));
                        return;
                    }

                    try {
                        boolean saved = trigger.saveData(settings, this.client);

                        if (saved) {
                            this.client.sendResponse(new WiredSavedComposer());
                            trigger.needsUpdate(true);
                            Emulator.getThreading().run(trigger);
                            WiredManager.invalidateRoom(room);
                        } else {
                            this.client.sendResponse(new UpdateFailedComposer("There was an error while saving that trigger"));
                        }
                    } catch (WiredTriggerSaveException e) {
                        this.client.sendResponse(new UpdateFailedComposer(e.getMessage()));
                    }
                }
            }
        }
    }
}
