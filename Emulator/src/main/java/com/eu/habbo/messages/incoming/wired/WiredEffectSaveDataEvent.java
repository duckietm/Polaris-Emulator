package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.generic.alerts.UpdateFailedComposer;
import com.eu.habbo.messages.outgoing.rooms.items.ItemStateComposer;
import com.eu.habbo.messages.outgoing.wired.WiredSavedComposer;

public class WiredEffectSaveDataEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int itemId = this.packet.readInt();

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room != null) {
            if (room.canModifyWired(this.client.getHabbo())) {
                InteractionWiredEffect effect = room.getRoomSpecialTypes().getEffect(itemId);
                InteractionWiredExtra extra = room.getRoomSpecialTypes().getExtra(itemId);

                try {
                    if (effect == null && extra == null)
                        throw new WiredSaveException(String.format("Wired effect/extra with item id %s not found in room", itemId));

                    WiredSettings settings;
                    try {
                        settings = InteractionWired.readSettings(this.packet, true);
                    } catch (IllegalArgumentException e) {
                        this.client.sendResponse(new UpdateFailedComposer("Invalid wired effect settings"));
                        return;
                    }
                    boolean saved;

                    if (effect != null) {
                        saved = effect.saveData(settings, this.client);
                    } else {
                        saved = extra.saveData(settings, this.client);
                    }

                    if (saved) {
                        this.client.sendResponse(new WiredSavedComposer());
                        if (effect != null) {
                            if (effect.isSelector()) {
                                if (effect.usesExistingSelectorTargets()) {
                                    effect.setExtradata("3");
                                    room.sendComposer(new ItemStateComposer(effect).compose());
                                } else if ("3".equals(effect.getExtradata()) || "4".equals(effect.getExtradata()) || "5".equals(effect.getExtradata())) {
                                    effect.setExtradata("0");
                                    room.sendComposer(new ItemStateComposer(effect).compose());
                                }
                            }

                            effect.needsUpdate(true);
                            Emulator.getThreading().run(effect);
                        } else {
                            extra.needsUpdate(true);
                            Emulator.getThreading().run(extra);
                        }

                        // Invalidate wired cache when effect is saved
                        WiredManager.invalidateRoom(room);
                    } else {
                        this.client.sendResponse(new UpdateFailedComposer("There was an error while saving that effect"));
                    }
                }
                catch (WiredSaveException e) {
                    this.client.sendResponse(new UpdateFailedComposer(e.getMessage()));
                }
            }
        }
    }
}
