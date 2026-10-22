package com.eu.habbo.messages.incoming.collectibles;

import com.eu.habbo.habbohotel.collectibles.CollectibleData.Owned;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.Product;
import com.eu.habbo.habbohotel.collectibles.CollectiblesService;
import com.eu.habbo.habbohotel.collectibles.CollectiblesSettings;
import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.collectibles.CollectibleRewardBoxOpenedComposer;
import com.eu.habbo.messages.outgoing.collectibles.CollectibleRewardBoxResultComposer;
import com.eu.habbo.messages.outgoing.rooms.UpdateStackHeightComposer;
import com.eu.habbo.messages.outgoing.rooms.items.RemoveFloorItemComposer;
import java.util.List;
import java.util.Optional;

/**
 * AIR 15 reward box open (1489, {@code objectId}), sent after the user confirms opening a
 * {@code furniture_nft_reward_box} furni of theirs. The box goes and one of its prizes
 * ({@code collectible_reward_boxes}) is minted to the user; the room sees {@code 3374} with who got what.
 * {@code 2692} answers 0 ok, 1 failed.
 */
public class OpenCollectibleRewardBoxEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());

        CollectiblesSettings settings = CollectiblesSupport.settings();
        CollectiblesService service = CollectiblesSupport.service(settings);
        if (service == null) return;

        Room room = habbo.getHabboInfo().getCurrentRoom();
        HabboItem box = room == null ? null : room.getHabboItem(itemId);
        if (box == null
                || box.getUserId() != habbo.getHabboInfo().getId()
                || service.boxPrizes(box.getBaseItem().getId()).isEmpty()) {
            this.client.sendResponse(new CollectibleRewardBoxResultComposer(1));
            return;
        }

        Optional<Owned> owned;
        synchronized (box) {
            if (room.getHabboItem(itemId) == null) return;
            owned = service.openBox(
                    habbo.getHabboInfo().getId(), box.getBaseItem().getId(), CollectiblesSupport.now());
            if (owned.isEmpty()) {
                this.client.sendResponse(new CollectibleRewardBoxResultComposer(1));
                return;
            }
            room.removeHabboItem(box);
        }

        room.sendComposer(new RemoveFloorItemComposer(box).compose());
        RoomTile tile = room.getLayout().getTile(box.getX(), box.getY());
        if (tile != null) {
            tile.setStackHeight(room.getStackHeight(box.getX(), box.getY(), false));
            room.updateTile(tile);
            room.sendComposer(
                    new UpdateStackHeightComposer(box.getX(), box.getY(), tile.z, tile.relativeHeight()).compose());
        }
        box.setRoomId(0);
        CollectiblesSupport.deleteItem(box);

        Product reward = service.product(owned.get().productCode()).orElse(null);
        if (reward != null) {
            room.sendComposer(new CollectibleRewardBoxOpenedComposer(
                            CollectibleRewardBoxOpenedComposer.STATE_START,
                            habbo.getHabboInfo().getId(),
                            reward)
                    .compose());
            room.sendComposer(new CollectibleRewardBoxOpenedComposer(
                            CollectibleRewardBoxOpenedComposer.STATE_FINISH,
                            habbo.getHabboInfo().getId(),
                            reward)
                    .compose());
        }
        CollectiblesSupport.publishAcquired(habbo, List.of(owned.get()), service, settings);
        this.client.sendResponse(new CollectibleRewardBoxResultComposer(0));
    }
}
