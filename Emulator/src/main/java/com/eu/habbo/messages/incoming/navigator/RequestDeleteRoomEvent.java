package com.eu.habbo.messages.incoming.navigator;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.guilds.Guild;
import com.eu.habbo.habbohotel.modtool.ScripterManager;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.pets.RideablePet;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.inventory.AddPetComposer;
import com.eu.habbo.plugin.events.navigator.NavigatorRoomDeletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class RequestDeleteRoomEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestDeleteRoomEvent.class);

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        int roomId = this.packet.readInt();

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(roomId);

        if (room != null) {
            if (room.isOwner(this.client.getHabbo())) {
                if (room.getId() == this.client.getHabbo().getHabboInfo().getHomeRoom()) {
                    return;
                }

                if (Emulator.getPluginManager().fireEvent(new NavigatorRoomDeletedEvent(this.client.getHabbo(), room)).isCancelled()) {
                    return;
                }

                room.ejectAll();
                room.ejectUserFurni(room.getOwnerId());

                List<Bot> bots = new ArrayList<>(room.getCurrentBots().values());
                for (Bot bot : bots) {
                    Emulator.getGameEnvironment().getBotManager().pickUpBot(bot, null);
                }

                List<Pet> pets = new ArrayList<>(room.getCurrentPets().values());
                for (Pet pet : pets) {
                    if (pet instanceof RideablePet) {
                        RideablePet rideablePet = (RideablePet) pet;
                        if (rideablePet.getRider() != null) {
                            rideablePet.getRider().getHabboInfo().dismountPet(true);
                        }
                    }

                    pet.removeFromRoom();
                    Emulator.getThreading().run(pet);

                    Habbo owner = Emulator.getGameEnvironment().getHabboManager().getHabbo(pet.getUserId());

                    if (owner != null) {
                        owner.getClient().sendResponse(new AddPetComposer(pet));
                        owner.getInventory().getPetsComponent().addPet(pet);
                    }
                }

                if (room.getGuildId() > 0) {
                    Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(room.getGuildId());

                    if (guild != null) {
                        Emulator.getGameEnvironment().getGuildManager().deleteGuild(guild);
                    }
                }

                room.preventUnloading = false;
                room.dispose();
                Emulator.getGameEnvironment().getRoomManager().uncacheRoom(room);

                try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
                    try (PreparedStatement statement = connection.prepareStatement("DELETE FROM rooms WHERE id = ? LIMIT 1")) {
                        statement.setInt(1, roomId);
                        statement.execute();
                    }

                    if (room.hasCustomLayout()) {
                        try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM room_models_custom WHERE id = ? LIMIT 1")) {
                            stmt.setInt(1, roomId);
                            stmt.execute();
                        }
                    }

                    Emulator.getGameEnvironment().getRoomManager().unloadRoom(room);

                    try (PreparedStatement rights = connection.prepareStatement("DELETE FROM room_rights WHERE room_id = ?")) {
                        rights.setInt(1, roomId);
                        rights.execute();
                    }

                    try (PreparedStatement votes = connection.prepareStatement("DELETE FROM room_votes WHERE room_id = ?")) {
                        votes.setInt(1, roomId);
                        votes.execute();
                    }

                    try (PreparedStatement filter = connection.prepareStatement("DELETE FROM room_wordfilter WHERE room_id = ?")) {
                        filter.setInt(1, roomId);
                        filter.execute();
                    }
                } catch (SQLException e) {
                    LOGGER.error("Caught SQL exception", e);
                }
            } else {
                String message = Emulator.getTexts().getValue("scripter.warning.room.delete").replace("%username%", this.client.getHabbo().getHabboInfo().getUsername()).replace("%roomname%", room.getName()).replace("%roomowner%", room.getOwnerName());
                ScripterManager.scripterDetected(this.client, message);
                LOGGER.info(message);
            }
        }
    }
}
