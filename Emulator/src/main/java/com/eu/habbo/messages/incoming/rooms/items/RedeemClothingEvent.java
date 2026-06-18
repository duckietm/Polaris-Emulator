package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.ClothItem;
import com.eu.habbo.habbohotel.items.interactions.InteractionClothing;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.rooms.UpdateStackHeightComposer;
import com.eu.habbo.messages.outgoing.rooms.items.RemoveFloorItemComposer;
import com.eu.habbo.messages.outgoing.users.UserClothesComposer;
import com.eu.habbo.threading.runnables.QueryDeleteHabboItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class RedeemClothingEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedeemClothingEvent.class);

    @Override
    public void handle() throws Exception {
        int itemId = this.packet.readInt();

        if (!RoomItemInputGuard.isPositiveId(itemId))
            return;

        if (this.client.getHabbo().getHabboInfo().getCurrentRoom() != null &&
                this.client.getHabbo().getHabboInfo().getCurrentRoom().hasRights(this.client.getHabbo())) {
            HabboItem item = this.client.getHabbo().getHabboInfo().getCurrentRoom().getHabboItem(itemId);

            if (item != null && item.getUserId() == this.client.getHabbo().getHabboInfo().getId()) {
                if (item instanceof InteractionClothing) {
                    ClothItem clothing = Emulator.getGameEnvironment().getCatalogManager().getClothing(item.getBaseItem().getName());

                    if (clothing != null) {
                        if (!this.client.getHabbo().getInventory().getWardrobeComponent().getClothing().contains(clothing.id)) {
                            if (!this.grantClothing(clothing.id)) {
                                return;
                            }

                            item.setRoomId(0);
                            RoomTile tile = this.client.getHabbo().getHabboInfo().getCurrentRoom().getLayout().getTile(item.getX(), item.getY());
                            if (tile == null) {
                                return;
                            }

                            this.client.getHabbo().getHabboInfo().getCurrentRoom().removeHabboItem(item);
                            this.client.getHabbo().getHabboInfo().getCurrentRoom().updateTile(tile);
                            this.client.getHabbo().getHabboInfo().getCurrentRoom().sendComposer(new UpdateStackHeightComposer(tile.x, tile.y, tile.z, tile.relativeHeight()).compose());
                            this.client.getHabbo().getHabboInfo().getCurrentRoom().sendComposer(new RemoveFloorItemComposer(item, true).compose());
                            Emulator.getThreading().run(new QueryDeleteHabboItem(item.getId()));

                            this.client.getHabbo().getInventory().getWardrobeComponent().getClothing().add(clothing.id);
                            this.client.getHabbo().getInventory().getWardrobeComponent().getClothingSets().addAll(clothing.setId);
                            this.client.sendResponse(new UserClothesComposer(this.client.getHabbo()));
                            this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FIGURESET_REDEEMED.key));

                        } else {
                            this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FIGURESET_OWNED_ALREADY.key));
                        }
                    } else {
                        LOGGER.error("[Catalog] No definition in catalog_clothing found for clothing name {}. Could not redeem clothing!", item.getBaseItem().getName());
                    }
                }
            }
        }
    }

    private boolean grantClothing(int clothingId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("INSERT INTO users_clothing (user_id, clothing_id) VALUES (?, ?)")) {
            statement.setInt(1, this.client.getHabbo().getHabboInfo().getId());
            statement.setInt(2, clothingId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
            return false;
        }
    }
}
