package polaris.hotel.rooms;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import polaris.hotel.GameEnvironment;
import polaris.hotel.bots.Bot;
import polaris.hotel.guilds.Guild;
import polaris.hotel.pets.Pet;
import polaris.hotel.pets.RideablePet;
import polaris.hotel.users.User;
import polaris.messages.outgoing.inventory.AddPetComposer;

/**
 * Deletes a room the way its owner does from the navigator: everyone leaves,
 * furni, bots and pets go back to their owners, the room's group is removed,
 * and the room with its custom model, rights, votes and word filter is dropped.
 *
 * <p>The room must be loaded with its data, otherwise there are no items to
 * give back. Callers pass the services they already hold, so this class needs
 * no global access.
 */
public final class RoomDeleter {
    private RoomDeleter() {}

    /**
     * @param saveAsync runs a pet's save; the navigator hands it to the thread
     *     pool, housekeeping may run it in place
     */
    public static void delete(
            Room room, GameEnvironment environment, Connection connection, Consumer<Runnable> saveAsync)
            throws SQLException {
        int roomId = room.getId();

        room.ejectAll();
        room.ejectUserFurni(room.getOwnerId());

        List<Bot> bots = new ArrayList<>(room.getCurrentBots().values());
        for (Bot bot : bots) {
            environment.getBotManager().pickUpBot(bot, null);
        }

        List<Pet> pets = new ArrayList<>(room.getCurrentPets().values());
        for (Pet pet : pets) {
            if (pet instanceof RideablePet rideablePet && rideablePet.getRider() != null) {
                rideablePet.getRider().getUserInfo().dismountPet(true);
            }

            pet.removeFromRoom();
            saveAsync.accept(pet);

            User owner = environment.getUserManager().getUser(pet.getUserId());

            if (owner != null) {
                owner.getClient().sendResponse(new AddPetComposer(pet));
                owner.getInventory().getPetsComponent().addPet(pet);
            }
        }

        if (room.getGuildId() > 0) {
            Guild guild = environment.getGuildManager().getGuild(room.getGuildId());

            if (guild != null) {
                environment.getGuildManager().deleteGuild(guild);
            }
        }

        room.preventUnloading = false;
        room.dispose();
        environment.getRoomManager().uncacheRoom(room);

        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM rooms WHERE id = ? LIMIT 1")) {
            statement.setInt(1, roomId);
            statement.execute();
        }

        if (room.hasCustomLayout()) {
            try (PreparedStatement statement =
                    connection.prepareStatement("DELETE FROM room_models_custom WHERE id = ? LIMIT 1")) {
                statement.setInt(1, roomId);
                statement.execute();
            }
        }

        environment.getRoomManager().unloadRoom(room);

        for (String table : new String[] {"room_rights", "room_votes", "room_wordfilter"}) {
            try (PreparedStatement statement =
                    connection.prepareStatement("DELETE FROM " + table + " WHERE room_id = ?")) {
                statement.setInt(1, roomId);
                statement.execute();
            }
        }
    }
}
