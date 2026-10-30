package com.eu.habbo.messages.incoming.rooms.pets;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.pets.MonsterplantPet;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.items.AddFloorItemComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class CompostMonsterplantEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CompostMonsterplantEvent.class);

    @Override
    public void handle() throws Exception {
        int petId = this.packet.readInt();

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null) return;
        
        Pet pet = room.getPet(petId);

        if (pet != null) {
            if (pet instanceof MonsterplantPet) {
                if (pet.getUserId() == this.client.getHabbo().getHabboInfo().getId()) {
                    if (((MonsterplantPet) pet).isDead()) {
                        Item baseItem = Emulator.getGameEnvironment().getItemManager().getItem("mnstr_compost");

                        if (baseItem != null) {
                            HabboItem compost = Emulator.getGameEnvironment().getItemManager().createItem(pet.getUserId(), baseItem, 0, 0, "");

                            // A compost that could not be created leaves the plant where it is.
                            if (compost == null) return;

                            compost.setX(pet.getRoomUnit().getX());
                            compost.setY(pet.getRoomUnit().getY());
                            compost.setZ(pet.getRoomUnit().getZ());
                            compost.setRotation(pet.getRoomUnit().getBodyRotation().getValue());
                            // Saved like any placed furni: without the room id and a save it went back to the
                            // inventory when the room reloaded.
                            compost.setRoomId(room.getId());
                            compost.needsUpdate(true);
                            room.addHabboItem(compost);
                            room.sendComposer(new AddFloorItemComposer(compost, this.client.getHabbo().getHabboInfo().getUsername()).compose());
                        }

                        pet.removeFromRoom();
                        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("DELETE FROM users_pets WHERE id = ? LIMIT 1")) {
                            statement.setInt(1, pet.getId());
                            statement.executeUpdate();
                        } catch (SQLException e) {
                            LOGGER.error("Caught SQL exception", e);
                        }
                    }
                }
            }
        }
    }
}