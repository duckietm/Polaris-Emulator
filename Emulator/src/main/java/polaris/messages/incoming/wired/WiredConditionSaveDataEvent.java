package polaris.messages.incoming.wired;

import polaris.Emulator;
import polaris.hotel.items.interactions.InteractionWired;
import polaris.hotel.items.interactions.InteractionWiredCondition;
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
import polaris.messages.outgoing.wired.WiredSavedComposer;

public class WiredConditionSaveDataEvent extends MessageHandler {
    /** A save dropped without an answer. */
    private static final class DroppedSave extends RuntimeException {
        DroppedSave() {
            super(null, null, false, false);
        }
    }

    private static final WiredConditionSaveAdapter SAVE_ADAPTER = new WiredConditionSaveAdapter();

    @Override
    public void handle() throws Exception {
        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());

        Room room = this.client.getUser().getUserInfo().getCurrentRoom();

        if (room != null) {
            if (room.canModifyWired(this.client.getUser())) {
                InteractionWiredCondition condition = room.getRoomSpecialTypes().getCondition(itemId);

                if (condition != null) {
                    boolean saved;
                    try {
                        saved = SAVE_ADAPTER.save(
                                condition,
                                () -> {
                                    int maximumStringLength = condition instanceof WiredLargePayload
                                            ? WiredLargePayload.MAX_STRING_PARAM_LENGTH
                                            : WiredInputGuard.MAX_STRING_PARAM_LENGTH;
                                    WiredSettings settings = BuildersClubItemIds.fromWire(
                                            InteractionWired.readSettings(
                                                    this.packet,
                                                    InteractionWired.SaveKind.CONDITION,
                                                    maximumStringLength),
                                            room);
                                    settings.setRoom(room);
                                    // AIR drops a save with more than 20 picks without an answer.
                                    if (WiredSaveGate.dropsSilently(settings)) throw new DroppedSave();
                                    WiredSaveGate.keepPicksOnce(settings);
                                    String sourceRefusal = WiredSourceSaveRules.apply(condition, settings, room);
                                    if (sourceRefusal != null) throw new WiredSaveRefusedException(sourceRefusal);
                                    return settings;
                                },
                                () -> this.packet);
                    } catch (DroppedSave dropped) {
                        return;
                    } catch (WiredSaveRefusedException refused) {
                        this.client.sendResponse(
                                new UpdateFailedComposer(refused.getMessage(), refused.getParameters()));
                        return;
                    } catch (IllegalArgumentException exception) {
                        this.client.sendResponse(new UpdateFailedComposer("Invalid wired condition settings"));
                        return;
                    }

                    if (!saved) {
                        this.client.sendResponse(
                                new UpdateFailedComposer("There was an error while saving that condition"));
                        return;
                    }

                    condition.markEditorSaved();
                    this.client.sendResponse(new WiredSavedComposer());
                    condition.needsUpdate(true);
                    Emulator.getThreading().run(condition);
                    WiredManager.invalidateRoom(room);
                }
            }
        }
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
