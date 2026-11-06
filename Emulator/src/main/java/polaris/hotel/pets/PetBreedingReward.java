package polaris.hotel.pets;

import java.sql.ResultSet;
import java.sql.SQLException;

public class PetBreedingReward {

    public final int petType;

    public final int rarityLevel;

    public final int breed;

    public PetBreedingReward(ResultSet set) throws SQLException {
        this.petType = set.getInt("pet_type");
        this.rarityLevel = set.getInt("rarity_level");
        this.breed = set.getInt("breed");
    }
}
