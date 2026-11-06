package polaris.messages.incoming.rooms.pets;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.items.Item;
import polaris.hotel.pets.HorsePet;
import polaris.hotel.pets.Pet;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.inventory.AddUserItemComposer;
import polaris.messages.outgoing.inventory.InventoryRefreshComposer;
import polaris.messages.outgoing.rooms.pets.RoomPetHorseFigureComposer;

public class HorseRemoveSaddleEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(HorseRemoveSaddleEvent.class);

    @Override
    public void handle() throws Exception {
        Room room = this.client.getUser().getUserInfo().getCurrentRoom();
        if (room == null) return;

        Pet pet = room.getPet(this.packet.readInt());

        if (pet == null
                || !(pet instanceof HorsePet)
                || pet.getUserId() != this.client.getUser().getUserInfo().getId()) return;

        HorsePet horse = (HorsePet) pet;

        if (!horse.hasSaddle()) return;

        int saddleItemId = horse.getSaddleItemId();

        if (saddleItemId == 0) { // backwards compatibility: horses could be missing the saddle item ID
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "SELECT id FROM items_base WHERE item_name LIKE 'horse_saddle%' LIMIT 1")) {
                try (ResultSet set = statement.executeQuery()) {
                    if (set.next()) {
                        saddleItemId = set.getInt("id");
                    } else {
                        LOGGER.error(
                                "There is no viable fallback saddle item for old horses with no saddle item ID. Horse pet ID: {}",
                                horse.getId());
                        return;
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }

        Item saddleItem = Emulator.getGameEnvironment().getItemManager().getItem(saddleItemId);

        if (saddleItem == null) return;

        // The saddle item first: the horse lost its saddle even when the item could not be created, and the owner got
        // nothing back.
        UserItem saddle = Emulator.getGameEnvironment()
                .getItemManager()
                .createItem(this.client.getUser().getUserInfo().getId(), saddleItem, 0, 0, "");

        if (saddle == null) return;

        horse.hasSaddle(false);
        horse.needsUpdate = true;
        Emulator.getThreading().run(pet);
        room.sendComposer(new RoomPetHorseFigureComposer(horse).compose());

        this.client.getUser().getInventory().getItemsComponent().addItem(saddle);

        this.client.sendResponse(new AddUserItemComposer(saddle));
        this.client.sendResponse(new InventoryRefreshComposer());
    }
}
