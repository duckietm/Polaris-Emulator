package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredInputGuard;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredLargePayload;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSaveRefusedException;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.protocol.WiredSourceSaveRules;
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
                        throw new WiredSaveException(
                                String.format("Wired effect/extra with item id %s not found in room", itemId));

                    WiredSettings settings;
                    try {
                        int maximumStringLength =
                                effect instanceof WiredLargePayload || extra instanceof WiredLargePayload
                                        ? WiredLargePayload.MAX_STRING_PARAM_LENGTH
                                        : WiredInputGuard.MAX_STRING_PARAM_LENGTH;
                        settings = InteractionWired.readSettings(this.packet, this.saveKind(), maximumStringLength);
                        settings.setRoom(room);
                    } catch (IllegalArgumentException e) {
                        this.client.sendResponse(new UpdateFailedComposer("Invalid wired effect settings"));
                        return;
                    }

                    String sourceRefusal = WiredSourceSaveRules.apply(effect != null ? effect : extra, settings, room);
                    if (sourceRefusal != null) throw new WiredSaveException(sourceRefusal);
                    boolean saved;

                    if (effect != null) {
                        saved = effect.saveData(settings, this.client);
                    } else {
                        saved = extra.saveData(settings, this.client);
                    }

                    if (saved) {
                        if (effect != null) effect.markEditorSaved();
                        else extra.markEditorSaved();
                        this.client.sendResponse(new WiredSavedComposer());
                        if (effect != null) {
                            if (effect.isSelector()) {
                                // Every selector save sends its resting state, as in Habbo: 3 with Filter, 0 without
                                // (Invert does not change it). Behaviour observed in plus-emulator/PlusEMU 74b6b1b0.
                                effect.setExtradata(selectorRestingState(effect));
                                room.sendComposer(new ItemStateComposer(effect).compose());
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
                        this.client.sendResponse(
                                new UpdateFailedComposer("There was an error while saving that effect"));
                    }
                } catch (WiredSaveException | WiredSaveRefusedException e) {
                    this.client.sendResponse(new UpdateFailedComposer(e.getMessage()));
                }
            }
        }
    }

    /** A selector rests at 3 when it filters the existing selection and at 0 otherwise. */
    static String selectorRestingState(InteractionWiredEffect selector) {
        return selector.usesExistingSelectorTargets() ? "3" : "0";
    }

    /**
     * Which official save packet this is. Effects and extras share this handler; the official addon,
     * selector and variable saves only differ in the slot after the furni picks.
     */
    protected InteractionWired.SaveKind saveKind() {
        return InteractionWired.SaveKind.ACTION;
    }

    /** One shared cooldown for every wired save, so alternating box types cannot multiply it. */
    @Override
    public int getRatelimit() {
        return 200;
    }

    @Override
    public String getRatelimitGroup() {
        return "wired.save";
    }
}
