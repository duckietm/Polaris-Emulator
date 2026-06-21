package com.eu.habbo.messages.incoming.rooms.pets;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.pets.HorsePet;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.rooms.pets.RoomPetHorseFigureComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class HorseRemoveSaddleEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(HorseRemoveSaddleEvent.class);

    @Override
    public void handle() throws Exception {
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        Pet pet = room.getPet(this.packet.readInt());

        if (pet == null || !(pet instanceof HorsePet horse) || pet.getUserId() != this.client.getHabbo().getHabboInfo().getId()) return;

        if (!horse.hasSaddle()) return;

        int saddleItemId = horse.getSaddleItemId();

        if (saddleItemId == 0) { // backwards compatibility: horses could be missing the saddle item ID
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT id FROM items_base WHERE item_name LIKE 'horse_saddle%' LIMIT 1")) {
                try (ResultSet set = statement.executeQuery()) {
                    if (set.next()) {
                        saddleItemId = set.getInt("id");
                    } else {
                        LOGGER.error("There is no viable fallback saddle item for old horses with no saddle item ID. Horse pet ID: {}", horse.getId());
                        return;
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }

        Item saddleItem = Emulator.getGameEnvironment().getItemManager().getItem(saddleItemId);

        if (saddleItem == null) return;

        horse.hasSaddle(false);
        horse.needsUpdate = true;
        Emulator.getThreading().run(pet);
        this.client.getHabbo().getHabboInfo().getCurrentRoom().sendComposer(new RoomPetHorseFigureComposer(horse).compose());

        HabboItem saddle = Emulator.getGameEnvironment().getItemManager().createItem(this.client.getHabbo().getHabboInfo().getId(), saddleItem, 0, 0, "");

        this.client.getHabbo().getInventory().getItemsComponent().addItem(saddle);

        this.client.sendResponse(new AddHabboItemComposer(saddle));
        this.client.sendResponse(new InventoryRefreshComposer());
    }
}
