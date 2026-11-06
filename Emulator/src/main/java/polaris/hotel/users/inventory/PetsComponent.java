package polaris.hotel.users.inventory;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.pets.Pet;
import polaris.hotel.pets.PetManager;
import polaris.hotel.users.User;

public class PetsComponent {
    private static final Logger LOGGER = LoggerFactory.getLogger(PetsComponent.class);
    private final Int2ObjectMap<Pet> pets = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());

    public PetsComponent(User user) {
        this.loadPets(user);
    }

    private void loadPets(User user) {
        synchronized (this.pets) {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                    PreparedStatement statement =
                            connection.prepareStatement("SELECT * FROM users_pets WHERE user_id = ? AND room_id = 0")) {
                statement.setInt(1, user.getUserInfo().getId());

                try (ResultSet set = statement.executeQuery()) {
                    while (set.next()) {
                        this.pets.put(set.getInt("id"), PetManager.loadPet(set));
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }
    }

    public Pet getPet(int id) {
        return this.pets.get(id);
    }

    public void addPet(Pet pet) {
        synchronized (this.pets) {
            this.pets.put(pet.getId(), pet);
        }
    }

    public void addPets(Set<Pet> pets) {
        synchronized (this.pets) {
            for (Pet p : pets) {
                this.pets.put(p.getId(), p);
            }
        }
    }

    public void removePet(Pet pet) {
        synchronized (this.pets) {
            this.pets.remove(pet.getId());
        }
    }

    public Int2ObjectMap<Pet> getPets() {
        return this.pets;
    }

    public int getPetsCount() {
        return this.pets.size();
    }

    public void dispose() {
        synchronized (this.pets) {
            for (Pet pet : this.pets.values()) {
                if (pet.needsUpdate) Emulator.getThreading().run(pet);
            }
        }
    }
}
