package polaris.messages.incoming.wired;

import polaris.Emulator;
import polaris.hotel.items.interactions.InteractionWired;
import polaris.hotel.items.interactions.InteractionWiredTrigger;
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

public class WiredTriggerSaveDataEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());

        Room room = this.client.getUser().getUserInfo().getCurrentRoom();

        if (room != null) {
            if (room.canModifyWired(this.client.getUser())) {
                InteractionWiredTrigger trigger = room.getRoomSpecialTypes().getTrigger(itemId);

                if (trigger != null) {
                    WiredSettings settings;
                    try {
                        settings =
                                BuildersClubItemIds.fromWire(InteractionWired.readSettings(this.packet, false), room);
                        // As for effects and conditions: without the room a trigger skips the checks that need it.
                        settings.setRoom(room);
                    } catch (IllegalArgumentException e) {
                        this.client.sendResponse(new UpdateFailedComposer("Invalid wired trigger settings"));
                        return;
                    }

                    // AIR drops a save with more than 20 picks without an answer; a repeated pick is kept once.
                    if (WiredSaveGate.dropsSilently(settings)) return;
                    WiredSaveGate.keepPicksOnce(settings);

                    String sourceRefusal = WiredSourceSaveRules.apply(trigger, settings, room);
                    if (sourceRefusal != null) {
                        this.client.sendResponse(new UpdateFailedComposer(sourceRefusal));
                        return;
                    }

                    try {
                        boolean saved = trigger.saveData(settings, this.client);

                        if (saved) {
                            trigger.markEditorSaved();
                            this.client.sendResponse(new WiredSavedComposer());
                            trigger.needsUpdate(true);
                            Emulator.getThreading().run(trigger);
                            WiredManager.invalidateRoom(room);
                        } else {
                            this.client.sendResponse(
                                    new UpdateFailedComposer("There was an error while saving that trigger"));
                        }
                    } catch (WiredSaveRefusedException refused) {
                        this.client.sendResponse(
                                new UpdateFailedComposer(refused.getMessage(), refused.getParameters()));
                    } catch (WiredTriggerSaveException e) {
                        this.client.sendResponse(new UpdateFailedComposer(e.getMessage()));
                    }
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
