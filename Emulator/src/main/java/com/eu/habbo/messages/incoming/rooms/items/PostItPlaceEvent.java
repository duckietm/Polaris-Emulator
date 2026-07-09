package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.items.interactions.InteractionPostIt;
import com.eu.habbo.habbohotel.items.interactions.InteractionStickyPole;
import com.eu.habbo.habbohotel.rooms.FurnitureMovementError;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.inventory.RemoveHabboItemComposer;
import com.eu.habbo.messages.outgoing.rooms.items.AddWallItemComposer;
import com.eu.habbo.messages.outgoing.rooms.items.PostItStickyPoleOpenComposer;

public class PostItPlaceEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        int itemId = this.packet.readInt();
        String location = this.packet.readString();

        if (!RoomItemInputGuard.isPositiveId(itemId) || !RoomItemInputGuard.isValidWallPosition(location))
            return;

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room != null) {
            if (room.hasRights(this.client.getHabbo()) || !room.getRoomSpecialTypes().getItemsOfType(InteractionStickyPole.class).isEmpty()) {
                HabboItem item = this.client.getHabbo().getInventory().getItemsComponent().getHabboItem(itemId);

                if (item instanceof InteractionPostIt) {
                    if (room.getPostItNotes().size() < Room.MAXIMUM_POSTITNOTES) {
                        room.addHabboItem(item);
                        item.setExtradata("FFFF33");
                        item.setRoomId(this.client.getHabbo().getHabboInfo().getCurrentRoom().getId());
                        item.setWallPosition(location);
                        item.setUserId(this.client.getHabbo().getHabboInfo().getId());
                        item.needsUpdate(true);
                        room.sendComposer(new AddWallItemComposer(item, this.client.getHabbo().getHabboInfo().getUsername()).compose());
                        this.client.getHabbo().getInventory().getItemsComponent().removeHabboItem(item);
                        this.client.sendResponse(new RemoveHabboItemComposer(item.getGiftAdjustedId()));
                        item.setFromGift(false);
                        Emulator.getThreading().run(item);

                        if (room.getOwnerId() != this.client.getHabbo().getHabboInfo().getId()) {
                            AchievementManager.progressAchievement(room.getOwnerId(), Emulator.getGameEnvironment().getAchievementManager().getAchievement("NotesReceived"));
                            AchievementManager.progressAchievement(this.client.getHabbo().getHabboInfo().getId(), Emulator.getGameEnvironment().getAchievementManager().getAchievement("NotesLeft"));
                        }

                        if (!room.hasRights(this.client.getHabbo())) {
                            this.client.sendResponse(new PostItStickyPoleOpenComposer(item));
                        }
                    }
                    else {
                        this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, FurnitureMovementError.MAX_STICKIES.errorCode));
                    }
                }
            }
        }
    }
}
