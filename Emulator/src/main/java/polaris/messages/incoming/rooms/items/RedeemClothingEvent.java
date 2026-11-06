package polaris.messages.incoming.rooms.items;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.catalog.ClothItem;
import polaris.hotel.items.interactions.InteractionClothing;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.generic.alerts.BubbleAlertComposer;
import polaris.messages.outgoing.generic.alerts.BubbleAlertKeys;
import polaris.messages.outgoing.rooms.UpdateStackHeightComposer;
import polaris.messages.outgoing.rooms.items.RemoveFloorItemComposer;
import polaris.messages.outgoing.users.UserClothesComposer;
import polaris.threading.runnables.QueryDeleteUserItem;

public class RedeemClothingEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedeemClothingEvent.class);

    // A burst of redeems for the same furni wrote the clothing once per packet.
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());

        if (!RoomItemInputGuard.isPositiveId(itemId)) return;

        if (this.client.getUser().getUserInfo().getCurrentRoom() != null
                && this.client.getUser().getUserInfo().getCurrentRoom().hasRights(this.client.getUser())) {
            UserItem item = this.client.getUser().getUserInfo().getCurrentRoom().getUserItem(itemId);

            if (item != null
                    && item.getUserId() == this.client.getUser().getUserInfo().getId()) {
                if (item instanceof InteractionClothing) {
                    ClothItem clothing = Emulator.getGameEnvironment()
                            .getCatalogManager()
                            .getClothing(item.getBaseItem().getName());

                    if (clothing != null) {
                        if (!this.client
                                .getUser()
                                .getInventory()
                                .getWardrobeComponent()
                                .getClothing()
                                .contains(clothing.id)) {
                            // The tile first, the grant last: the clothing used to be written to the database before
                            // a missing tile returned, so the furni stayed in the room and the wardrobe in memory
                            // did not change.
                            RoomTile tile = this.client
                                    .getUser()
                                    .getUserInfo()
                                    .getCurrentRoom()
                                    .getLayout()
                                    .getTile(item.getX(), item.getY());
                            if (tile == null) {
                                return;
                            }

                            // Claimed before the grant: a pickup racing the redeem used to leave the furni in an
                            // inventory while the clothing was already granted, so it could be redeemed again.
                            Room room = this.client.getUser().getUserInfo().getCurrentRoom();
                            if (!room.claimItem(item)) {
                                return;
                            }

                            if (!this.grantClothing(clothing.id)) {
                                room.addUserItem(item);
                                return;
                            }

                            item.setRoomId(0);
                            this.client.getUser().getUserInfo().getCurrentRoom().updateTile(tile);
                            this.client
                                    .getUser()
                                    .getUserInfo()
                                    .getCurrentRoom()
                                    .sendComposer(
                                            new UpdateStackHeightComposer(tile.x, tile.y, tile.z, tile.relativeHeight())
                                                    .compose());
                            this.client
                                    .getUser()
                                    .getUserInfo()
                                    .getCurrentRoom()
                                    .sendComposer(new RemoveFloorItemComposer(item, true).compose());
                            Emulator.getThreading().runPersistence(new QueryDeleteUserItem(item.getId()));

                            this.client
                                    .getUser()
                                    .getInventory()
                                    .getWardrobeComponent()
                                    .getClothing()
                                    .add(clothing.id);
                            for (int setId : clothing.setId) {
                                this.client
                                        .getUser()
                                        .getInventory()
                                        .getWardrobeComponent()
                                        .getClothingSets()
                                        .add(setId);
                            }
                            this.client.sendResponse(new UserClothesComposer(this.client.getUser()));
                            this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FIGURESET_REDEEMED.key));

                        } else {
                            this.client.sendResponse(
                                    new BubbleAlertComposer(BubbleAlertKeys.FIGURESET_OWNED_ALREADY.key));
                        }
                    } else {
                        LOGGER.error(
                                "[Catalog] No definition in catalog_clothing found for clothing name {}. Could not redeem clothing!",
                                item.getBaseItem().getName());
                    }
                }
            }
        }
    }

    private boolean grantClothing(int clothingId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO users_clothing (user_id, clothing_id) VALUES (?, ?)")) {
            statement.setInt(1, this.client.getUser().getUserInfo().getId());
            statement.setInt(2, clothingId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
            return false;
        }
    }
}
