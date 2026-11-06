package polaris.messages.incoming.collectibles;

import java.util.List;
import java.util.Optional;
import polaris.hotel.collectibles.CollectibleData.Owned;
import polaris.hotel.collectibles.CollectibleData.Product;
import polaris.hotel.collectibles.CollectiblesService;
import polaris.hotel.collectibles.CollectiblesSettings;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.collectibles.CollectibleRewardBoxOpenedComposer;
import polaris.messages.outgoing.collectibles.CollectibleRewardBoxResultComposer;
import polaris.messages.outgoing.rooms.UpdateStackHeightComposer;
import polaris.messages.outgoing.rooms.items.RemoveFloorItemComposer;

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
        User user = this.client.getUser();
        if (user == null) return;

        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());

        CollectiblesSettings settings = CollectiblesSupport.settings();
        CollectiblesService service = CollectiblesSupport.service(settings);
        if (service == null) return;

        Room room = user.getUserInfo().getCurrentRoom();
        UserItem box = room == null ? null : room.getUserItem(itemId);
        if (box == null
                || box.getUserId() != user.getUserInfo().getId()
                || service.boxPrizes(box.getBaseItem().getId()).isEmpty()) {
            this.client.sendResponse(new CollectibleRewardBoxResultComposer(1));
            return;
        }

        Optional<Owned> owned;
        synchronized (box) {
            if (room.getUserItem(itemId) == null) return;
            owned = service.openBox(
                    user.getUserInfo().getId(), box.getBaseItem().getId(), CollectiblesSupport.now());
            if (owned.isEmpty()) {
                this.client.sendResponse(new CollectibleRewardBoxResultComposer(1));
                return;
            }
            room.removeUserItem(box);
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
                            user.getUserInfo().getId(),
                            reward)
                    .compose());
            room.sendComposer(new CollectibleRewardBoxOpenedComposer(
                            CollectibleRewardBoxOpenedComposer.STATE_FINISH,
                            user.getUserInfo().getId(),
                            reward)
                    .compose());
        }
        CollectiblesSupport.publishAcquired(user, List.of(owned.get()), service, settings);
        this.client.sendResponse(new CollectibleRewardBoxResultComposer(0));
    }
}
