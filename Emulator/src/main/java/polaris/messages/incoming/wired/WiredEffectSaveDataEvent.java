package polaris.messages.incoming.wired;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.items.interactions.InteractionWired;
import polaris.hotel.items.interactions.InteractionWiredEffect;
import polaris.hotel.items.interactions.InteractionWiredExtra;
import polaris.hotel.items.interactions.wired.WiredInputGuard;
import polaris.hotel.items.interactions.wired.WiredLargePayload;
import polaris.hotel.items.interactions.wired.WiredSaveRefusedException;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.protocol.WiredSaveGate;
import polaris.hotel.wired.protocol.WiredSourceSaveRules;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.generic.alerts.UpdateFailedComposer;
import polaris.messages.outgoing.rooms.items.ItemStateComposer;
import polaris.messages.outgoing.wired.WiredSavedComposer;

public class WiredEffectSaveDataEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredEffectSaveDataEvent.class);

    @Override
    public void handle() throws Exception {
        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());

        Room room = this.client.getUser().getUserInfo().getCurrentRoom();

        if (room != null) {
            if (room.canModifyWired(this.client.getUser())) {
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
                        settings = BuildersClubItemIds.fromWire(
                                InteractionWired.readSettings(this.packet, this.saveKind(), maximumStringLength), room);
                        settings.setRoom(room);
                    } catch (IllegalArgumentException e) {
                        this.client.sendResponse(new UpdateFailedComposer("Invalid wired effect settings"));
                        return;
                    }

                    // AIR drops a save with more than 20 picks without an answer; a repeated pick is kept once.
                    if (WiredSaveGate.dropsSilently(settings)) return;
                    WiredSaveGate.keepPicksOnce(settings);

                    String sourceRefusal = WiredSourceSaveRules.apply(effect != null ? effect : extra, settings, room);
                    if (sourceRefusal != null) throw new WiredSaveException(sourceRefusal);
                    String delayRefusal = WiredSaveGate.delayRefusal(settings);
                    if (delayRefusal != null) throw new WiredSaveRefusedException(delayRefusal);
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
                } catch (WiredSaveDroppedException dropped) {
                    // Dropped without an answer, as AIR's server does.
                    LOGGER.debug("Wired save of item {} dropped: {}", itemId, dropped.getMessage());
                } catch (WiredSaveRefusedException refused) {
                    this.client.sendResponse(new UpdateFailedComposer(refused.getMessage(), refused.getParameters()));
                } catch (WiredSaveException e) {
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
