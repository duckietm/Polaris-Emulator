package polaris.hotel.pets;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.pets.InteractionNest;
import polaris.hotel.items.interactions.pets.InteractionPetDrink;
import polaris.hotel.items.interactions.pets.InteractionPetFood;
import polaris.hotel.items.interactions.pets.InteractionPetToy;
import polaris.hotel.items.interactions.pets.InteractionPetTrampoline;
import polaris.hotel.pets.actions.ActionBeg;
import polaris.hotel.pets.actions.ActionBounce;
import polaris.hotel.pets.actions.ActionBreatheFire;
import polaris.hotel.pets.actions.ActionBreed;
import polaris.hotel.pets.actions.ActionChickenDance;
import polaris.hotel.pets.actions.ActionCount;
import polaris.hotel.pets.actions.ActionCroak;
import polaris.hotel.pets.actions.ActionDance;
import polaris.hotel.pets.actions.ActionDip;
import polaris.hotel.pets.actions.ActionDown;
import polaris.hotel.pets.actions.ActionDrink;
import polaris.hotel.pets.actions.ActionEat;
import polaris.hotel.pets.actions.ActionFlatten;
import polaris.hotel.pets.actions.ActionFollow;
import polaris.hotel.pets.actions.ActionFollowLeft;
import polaris.hotel.pets.actions.ActionFollowRight;
import polaris.hotel.pets.actions.ActionFree;
import polaris.hotel.pets.actions.ActionHang;
import polaris.hotel.pets.actions.ActionHere;
import polaris.hotel.pets.actions.ActionHighJump;
import polaris.hotel.pets.actions.ActionJump;
import polaris.hotel.pets.actions.ActionMambo;
import polaris.hotel.pets.actions.ActionMoveForward;
import polaris.hotel.pets.actions.ActionNest;
import polaris.hotel.pets.actions.ActionPlay;
import polaris.hotel.pets.actions.ActionPlayDead;
import polaris.hotel.pets.actions.ActionPlayFootball;
import polaris.hotel.pets.actions.ActionRelax;
import polaris.hotel.pets.actions.ActionRingOfFire;
import polaris.hotel.pets.actions.ActionRoll;
import polaris.hotel.pets.actions.ActionSilent;
import polaris.hotel.pets.actions.ActionSit;
import polaris.hotel.pets.actions.ActionSpeak;
import polaris.hotel.pets.actions.ActionSpin;
import polaris.hotel.pets.actions.ActionStand;
import polaris.hotel.pets.actions.ActionStay;
import polaris.hotel.pets.actions.ActionSwing;
import polaris.hotel.pets.actions.ActionSwitch;
import polaris.hotel.pets.actions.ActionTeleport;
import polaris.hotel.pets.actions.ActionTorch;
import polaris.hotel.pets.actions.ActionTripleJump;
import polaris.hotel.pets.actions.ActionTurnLeft;
import polaris.hotel.pets.actions.ActionTurnRight;
import polaris.hotel.pets.actions.ActionWagTail;
import polaris.hotel.pets.actions.ActionWave;
import polaris.hotel.pets.actions.ActionWings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;

public class PetManager {
    public static volatile int MAXIMUM_PET_INVENTORY_SIZE = 25;
    private static final Logger LOGGER = LoggerFactory.getLogger(PetManager.class);
    public static final int[] experiences = new int[] {
        100, 200, 400, 600, 900, 1300, 1800, 2400, 3200, 4300, 5700, 7600, 10100, 13300, 17500, 23000, 30200, 39600,
        51900
    };
    static int[] skins = new int[] {0, 1, 6, 7};
    public final Map<Integer, PetAction> petActions = new HashMap<Integer, PetAction>() {
        {
            this.put(0, new ActionFree());
            this.put(1, new ActionSit());
            this.put(2, new ActionDown());
            this.put(3, new ActionHere());
            this.put(4, new ActionBeg());
            this.put(5, new ActionPlayDead());
            this.put(6, new ActionStay());
            this.put(7, new ActionFollow());
            this.put(8, new ActionStand());
            this.put(9, new ActionJump());
            this.put(10, new ActionSpeak());
            this.put(11, new ActionPlay());
            this.put(12, new ActionSilent());
            this.put(13, new ActionNest());
            this.put(14, new ActionDrink());
            this.put(15, new ActionFollowLeft());
            this.put(16, new ActionFollowRight());
            this.put(17, new ActionPlayFootball());
            this.put(18, new ActionTeleport());
            this.put(19, new ActionBounce());
            this.put(20, new ActionFlatten());
            this.put(21, new ActionDance());
            this.put(22, new ActionSpin());
            this.put(23, new ActionSwitch());
            this.put(24, new ActionMoveForward());
            this.put(25, new ActionTurnLeft());
            this.put(26, new ActionTurnRight());
            this.put(27, new ActionRelax());
            this.put(28, new ActionCroak());
            this.put(29, new ActionDip());
            this.put(30, new ActionWave());
            this.put(31, new ActionMambo());
            this.put(32, new ActionHighJump());
            this.put(33, new ActionChickenDance());
            this.put(34, new ActionTripleJump());
            this.put(35, new ActionWings());
            this.put(36, new ActionBreatheFire());
            this.put(37, new ActionHang());
            this.put(38, new ActionTorch());
            this.put(40, new ActionSwing());
            this.put(41, new ActionRoll());
            this.put(42, new ActionRingOfFire());
            this.put(43, new ActionEat());
            this.put(44, new ActionWagTail());
            this.put(45, new ActionCount());
            this.put(46, new ActionBreed());
        }
    };
    // Built whole by reloadPetData and swapped in: a reload used to empty them while packets were reading.
    private volatile Map<Integer, Set<PetRace>> petRaces;
    private volatile Map<Integer, PetData> petData;
    private volatile Int2IntMap breedingPetType;
    private volatile Map<Integer, Int2ObjectMap<ArrayList<PetBreedingReward>>> breedingReward;

    public PetManager() {
        long millis = System.currentTimeMillis();

        this.petRaces = new HashMap<>();
        this.petData = new ConcurrentHashMap<>();
        this.breedingPetType = new Int2IntOpenHashMap();
        this.breedingReward = new HashMap<>();

        reloadPetData();

        LOGGER.info("Pet Manager -> Loaded! ({} MS)", System.currentTimeMillis() - millis);
    }

    public static int getLevel(int experience) {
        int index = -1;

        for (int i = 0; i < experiences.length; i++) {
            if (experiences[i] > experience) {
                index = i;
                break;
            }
        }

        if (index == -1) {
            index = experiences.length;
        }
        return index + 1;
    }

    public static int maxEnergy(int level) {
        return 100 * level;
    }

    public static int randomBody(int minimumRarity, boolean isRare) {
        int maxIndex = MonsterplantPet.bodyRarity.size();
        int randomIndex = isRare
                ? random(
                        Math.max(minimumRarity - 1, 0),
                        Math.min(maxIndex, (maxIndex - minimumRarity) + minimumRarity),
                        2.0)
                : random(Math.max(minimumRarity - 1, 0), maxIndex, 2.0);

        // Clamp to valid range to prevent ArrayIndexOutOfBoundsException
        randomIndex = Math.max(0, Math.min(randomIndex, maxIndex - 1));

        // Return the body type KEY (1-12), not the rarity value
        return (Integer) MonsterplantPet.bodyRarity.keySet().toArray()[randomIndex];
    }

    public static int randomColor(int minimumRarity, boolean isRare) {
        int maxIndex = MonsterplantPet.colorRarity.size();
        int randomIndex = isRare
                ? random(
                        Math.max(minimumRarity - 1, 0),
                        Math.min(maxIndex, (maxIndex - minimumRarity) + minimumRarity),
                        2.0)
                : random(Math.max(minimumRarity - 1, 0), maxIndex, 2.0);

        // Clamp to valid range to prevent ArrayIndexOutOfBoundsException
        randomIndex = Math.max(0, Math.min(randomIndex, maxIndex - 1));

        // Return the color hue KEY (0-10), not the rarity value
        return (Integer) MonsterplantPet.colorRarity.keySet().toArray()[randomIndex];
    }

    public static int random(int low, int high, double bias) {
        double r = Math.random();
        r = Math.pow(r, bias);
        return (int) (low + (high - low) * r);
    }

    public static Pet loadPet(ResultSet set) throws SQLException {
        if (set.getInt("type") == 15) return new HorsePet(set);
        else if (set.getInt("type") == 16) return new MonsterplantPet(set);
        else if (set.getInt("type") == 26 || set.getInt("type") == 27) return new GnomePet(set);
        else return new Pet(set);
    }

    public static NormalDistribution getNormalDistributionForBreeding(int levelOne, int levelTwo) {
        return getNormalDistributionForBreeding((levelOne + levelTwo) / 2);
    }

    public static NormalDistribution getNormalDistributionForBreeding(double avgLevel) {
        return new NormalDistribution(avgLevel, (20 - (avgLevel / 2)) / 2);
    }

    /**
     * Loads the pet tables into new maps and publishes them together. They used to be cleared first and filled after
     * the queries, so a pet bought during :update_pets found its type gone or its races missing; a database failure
     * now keeps the tables already loaded.
     */
    public void reloadPetData() {
        Map<Integer, Set<PetRace>> races = new HashMap<>();
        Map<Integer, PetData> data = new ConcurrentHashMap<>();
        Int2IntMap breedingTypes = new Int2IntOpenHashMap();
        Map<Integer, Int2ObjectMap<ArrayList<PetBreedingReward>>> breedingRewards = new HashMap<>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            loadRaces(connection, races);
            this.loadPetData(connection, data);
            this.loadPetCommands(connection, data);
            loadPetBreeding(connection, breedingTypes, breedingRewards);
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
            LOGGER.error("Pet Manager -> Failed to load!");
            return;
        }

        this.petRaces = races;
        this.petData = data;
        this.breedingPetType = breedingTypes;
        this.breedingReward = breedingRewards;
    }

    private static void loadRaces(Connection connection, Map<Integer, Set<PetRace>> petRaces) {
        try (Statement statement = connection.createStatement();
                ResultSet set =
                        statement.executeQuery("SELECT * FROM pet_breeds ORDER BY race, color_one, color_two ASC")) {
            while (set.next()) {
                petRaces.computeIfAbsent(set.getInt("race"), ignored -> new HashSet<>())
                        .add(new PetRace(set));
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    private void loadPetData(Connection connection, Map<Integer, PetData> petData) {
        try (Statement statement = connection.createStatement();
                ResultSet set = statement.executeQuery("SELECT * FROM pet_actions ORDER BY pet_type ASC")) {
            while (set.next()) {
                petData.put(set.getInt("pet_type"), new PetData(set));
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        loadPetItems(connection, petData);

        loadPetVocals(connection, petData);
    }

    private static void loadPetItems(Connection connection, Map<Integer, PetData> petData) {
        try (Statement statement = connection.createStatement();
                ResultSet set = statement.executeQuery("SELECT * FROM pet_items")) {
            while (set.next()) {
                Item baseItem = Emulator.getGameEnvironment().getItemManager().getItem(set.getInt("item_id"));

                if (baseItem != null) {
                    if (set.getInt("pet_id") == -1) {
                        if (baseItem.getInteractionType().getType() == InteractionNest.class)
                            PetData.generalNestItems.add(baseItem);
                        else if (baseItem.getInteractionType().getType() == InteractionPetFood.class)
                            PetData.generalFoodItems.add(baseItem);
                        else if (baseItem.getInteractionType().getType() == InteractionPetDrink.class)
                            PetData.generalDrinkItems.add(baseItem);
                        else if (baseItem.getInteractionType().getType() == InteractionPetToy.class
                                || baseItem.getInteractionType().getType() == InteractionPetTrampoline.class)
                            PetData.generalToyItems.add(baseItem);
                    } else {
                        PetData data = petData.get(set.getInt("pet_id"));

                        if (data != null) {
                            if (baseItem.getInteractionType().getType() == InteractionNest.class)
                                data.addNest(baseItem);
                            else if (baseItem.getInteractionType().getType() == InteractionPetFood.class)
                                data.addFoodItem(baseItem);
                            else if (baseItem.getInteractionType().getType() == InteractionPetDrink.class)
                                data.addDrinkItem(baseItem);
                            else if (baseItem.getInteractionType().getType() == InteractionPetToy.class
                                    || baseItem.getInteractionType().getType() == InteractionPetTrampoline.class)
                                data.addToyItem(baseItem);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    private static void loadPetVocals(Connection connection, Map<Integer, PetData> petData) {
        try (Statement statement = connection.createStatement();
                ResultSet set = statement.executeQuery("SELECT * FROM pet_vocals")) {
            while (set.next()) {
                if (set.getInt("pet_id") >= 0) {
                    if (petData.containsKey(set.getInt("pet_id"))) {
                        PetVocalsType petVocalsType =
                                PetVocalsType.valueOf(set.getString("type").toUpperCase());

                        if (petVocalsType != null) {
                            petData.get(set.getInt("pet_id"))
                                    .petVocals
                                    .get(petVocalsType)
                                    .add(new PetVocal(set.getString("message")));
                        } else {
                            LOGGER.error("Unknown pet vocal type {}", set.getString("type"));
                        }
                    } else {
                        LOGGER.error("Missing pet_actions table entry for pet id {}", set.getInt("pet_id"));
                    }
                } else {
                    if (!PetData.generalPetVocals.containsKey(
                            PetVocalsType.valueOf(set.getString("type").toUpperCase())))
                        PetData.generalPetVocals.put(
                                PetVocalsType.valueOf(set.getString("type").toUpperCase()), new HashSet<>());

                    PetData.generalPetVocals
                            .get(PetVocalsType.valueOf(set.getString("type").toUpperCase()))
                            .add(new PetVocal(set.getString("message")));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    private void loadPetCommands(Connection connection, Map<Integer, PetData> petData) {
        Map<Integer, PetCommand> commandsList = new HashMap<>();
        try (Statement statement = connection.createStatement();
                ResultSet set = statement.executeQuery("SELECT * FROM pet_commands_data")) {
            while (set.next()) {
                commandsList.put(
                        set.getInt("command_id"), new PetCommand(set, this.petActions.get(set.getInt("command_id"))));
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        try (Statement statement = connection.createStatement();
                ResultSet set = statement.executeQuery("SELECT * FROM pet_commands ORDER BY pet_id ASC")) {
            while (set.next()) {
                PetData data = petData.get(set.getInt("pet_id"));

                if (data != null) {
                    data.getPetCommands().add(commandsList.get(set.getInt("command_id")));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    private static void loadPetBreeding(
            Connection connection,
            Int2IntMap breedingPetType,
            Map<Integer, Int2ObjectMap<ArrayList<PetBreedingReward>>> breedingReward) {
        try (Statement statement = connection.createStatement();
                ResultSet set = statement.executeQuery("SELECT * FROM pet_breeding")) {
            while (set.next()) {
                breedingPetType.put(set.getInt("pet_id"), set.getInt("offspring_id"));
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        try (Statement statement = connection.createStatement();
                ResultSet set = statement.executeQuery("SELECT * FROM pet_breeding_races")) {
            while (set.next()) {
                PetBreedingReward reward = new PetBreedingReward(set);
                breedingReward
                        .computeIfAbsent(reward.petType, ignored -> new Int2ObjectOpenHashMap<>())
                        .computeIfAbsent(reward.rarityLevel, ignored -> new ArrayList<>())
                        .add(reward);
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public boolean isClubOnlyBreed(int petType, int color) {
        Set<PetRace> races = this.petRaces.get(petType);

        if (races == null) return false;

        for (PetRace race : races) {
            if (race.colorOne == color || race.colorTwo == color) return race.clubOnly;
        }

        return false;
    }

    public Set<PetRace> getBreeds(String petName) {
        if (!petName.matches("a0 pet\\d{1,3}")) {
            LOGGER.error("Pet data '{}' not found. Expected format: a0 pet<0-999>", petName);
            return null;
        }

        final int petId = Integer.parseInt(petName.substring(6));
        return this.petRaces.get(petId);
    }

    public Int2ObjectMap<ArrayList<PetBreedingReward>> getBreedingRewards(int petType) {
        return this.breedingReward.get(petType);
    }

    public int getRarityForOffspring(Pet pet) {
        final int[] rarityLevel = {0};

        Int2ObjectMap<ArrayList<PetBreedingReward>> offspringList =
                this.breedingReward.get(pet.getPetData().getType());

        for (Int2ObjectMap.Entry<ArrayList<PetBreedingReward>> entry : offspringList.int2ObjectEntrySet()) {
            for (PetBreedingReward reward : entry.getValue()) {
                if (reward.breed == pet.getRace()) {
                    rarityLevel[0] = entry.getIntKey();
                    return 4 - rarityLevel[0];
                }
            }
        }

        return 4 - rarityLevel[0];
    }

    public PetData getPetData(int type) {
        synchronized (this.petData) {
            if (this.petData.containsKey(type)) {
                return this.petData.get(type);
            } else {
                try (Connection connection =
                        Emulator.getDatabase().getDataSource().getConnection()) {
                    LOGGER.error("Missing petdata for type {}. Adding this to the database...", type);
                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO pet_actions (pet_type, pet_name, offspring_type, happy_actions, tired_actions, random_actions, can_swim) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                        statement.setInt(1, type);
                        statement.setString(2, getFallbackPetName(type));
                        statement.setInt(3, getFallbackOffspringType(type));
                        statement.setString(4, "");
                        statement.setString(5, "");
                        statement.setString(6, "");
                        statement.setString(7, "0");
                        statement.execute();
                    }

                    try (PreparedStatement statement =
                            connection.prepareStatement("SELECT * FROM pet_actions WHERE pet_type = ? LIMIT 1")) {
                        statement.setInt(1, type);
                        try (ResultSet set = statement.executeQuery()) {
                            if (set.next()) {
                                PetData petData = new PetData(set);
                                this.petData.put(type, petData);
                                LOGGER.error("Missing petdata for type {} added to the database!", type);
                                return petData;
                            }
                        }
                    }
                } catch (SQLException e) {
                    LOGGER.error("Caught SQL exception", e);
                }
            }
        }

        return null;
    }

    public PetData getPetData(String petName) {
        synchronized (this.petData) {
            for (Map.Entry<Integer, PetData> entry : this.petData.entrySet()) {
                if (entry.getValue().getName().equalsIgnoreCase(petName)) {
                    return entry.getValue();
                }
            }
        }

        return null;
    }

    public Collection<PetData> getPetData() {
        return this.petData.values();
    }

    private static String getFallbackPetName(int type) {
        switch (type) {
            case 0:
                return "Dog";
            case 1:
                return "Cat";
            case 2:
                return "Crocodile";
            case 3:
                return "Terrier";
            case 4:
                return "Bear";
            case 5:
                return "Pig";
            default:
                return "pet_type_" + type;
        }
    }

    private static int getFallbackOffspringType(int type) {
        switch (type) {
            case 0:
                return 29;
            case 1:
                return 28;
            case 3:
                return 25;
            case 4:
                return 24;
            case 5:
                return 30;
            default:
                return -1;
        }
    }

    public Pet createPet(Item item, String name, String race, String color, GameClient client) {
        int type = Integer.parseInt(item.getName().toLowerCase().replace("a0 pet", ""));

        if (this.petData.containsKey(type)) {
            Pet pet;
            if (type == 15)
                pet = new HorsePet(
                        type,
                        Integer.parseInt(race),
                        color,
                        name,
                        client.getUser().getUserInfo().getId());
            else if (type == 16) pet = this.createMonsterplant(null, client.getUser(), false, null, 0);
            else
                pet = new Pet(
                        type,
                        Integer.parseInt(race),
                        color,
                        name,
                        client.getUser().getUserInfo().getId());

            pet.needsUpdate = true;
            pet.run();
            return pet;
        }
        return null;
    }

    public Pet createPet(Connection connection, Item item, String name, String race, String color, GameClient client)
            throws SQLException {
        int type = Integer.parseInt(item.getName().toLowerCase().replace("a0 pet", ""));
        if (!this.petData.containsKey(type) || type == 16) return null;

        Pet pet = type == 15
                ? new HorsePet(
                        type,
                        Integer.parseInt(race),
                        color,
                        name,
                        client.getUser().getUserInfo().getId())
                : new Pet(
                        type,
                        Integer.parseInt(race),
                        color,
                        name,
                        client.getUser().getUserInfo().getId());
        pet.needsUpdate = true;
        pet.save(connection);
        return pet;
    }

    public Pet createPet(int type, String name, GameClient client) {
        Set<PetRace> races = this.petRaces.get(type);
        int raceCount = races == null ? 0 : races.size();
        return this.createPet(type, Emulator.getRandom().nextInt(raceCount + 1), name, client);
    }

    public Pet createPet(int type, int race, String name, GameClient client) {
        if (this.petData.containsKey(type)) {
            Pet pet = new Pet(
                    type, race, "FFFFFF", name, client.getUser().getUserInfo().getId());
            pet.needsUpdate = true;
            pet.run();
            return pet;
        }
        return null;
    }

    public MonsterplantPet createMonsterplant(Room room, User user, boolean rare, RoomTile t, int minimumRarity) {
        MonsterplantPet pet = new MonsterplantPet(
                user.getUserInfo().getId(), // Owner ID
                randomBody(minimumRarity, rare),
                randomColor(minimumRarity, rare),
                Emulator.getRandom().nextInt(12) + 1,
                Emulator.getRandom().nextInt(11),
                Emulator.getRandom().nextInt(12) + 1,
                Emulator.getRandom().nextInt(11),
                Emulator.getRandom().nextInt(12) + 1,
                Emulator.getRandom().nextInt(11));

        pet.setUserId(user.getUserInfo().getId());
        pet.setRoom(room);
        pet.setRoomUnit(new RoomUnit());
        pet.getRoomUnit().setPathFinderRoom(room);
        pet.needsUpdate = true;
        pet.run();
        return pet;
    }

    public Pet createGnome(String name, Room room, User user) {
        Pet pet = new GnomePet(
                26,
                0,
                "FFFFFF",
                name,
                user.getUserInfo().getId(),
                "5 " + "0 -1 "
                        + this.randomGnomeSkinColor() + " " + "1 10"
                        + (1 + Emulator.getRandom().nextInt(2)) + " " + this.randomGnomeColor() + " " + "2 201 "
                        + this.randomGnomeColor() + " " + "3 30"
                        + (1 + Emulator.getRandom().nextInt(2)) + " " + this.randomGnomeColor() + " " + "4 40"
                        + Emulator.getRandom().nextInt(2) + " " + this.randomGnomeColor());

        pet.setUserId(user.getUserInfo().getId());
        pet.setRoom(room);
        pet.setRoomUnit(new RoomUnit());
        pet.getRoomUnit().setPathFinderRoom(room);
        pet.needsUpdate = true;
        pet.run();

        return pet;
    }

    public Pet createLeprechaun(String name, Room room, User user) {
        Pet pet = new GnomePet(
                27,
                0,
                "FFFFFF",
                name,
                user.getUserInfo().getId(),
                "5 " + "0 -1 0 " + "1 102 19 " + "2 201 27 " + "3 302 23 " + "4 401 27");

        pet.setUserId(user.getUserInfo().getId());
        pet.setRoom(room);
        pet.setRoomUnit(new RoomUnit());
        pet.getRoomUnit().setPathFinderRoom(room);
        pet.needsUpdate = true;
        pet.run();

        return pet;
    }

    private int randomGnomeColor() {
        int color = 19;

        while (color == 19 || color == 27) {
            color = Emulator.getRandom().nextInt(34);
        }

        return color;
    }

    @SuppressWarnings("unused")
    private int randomLeprechaunColor() {
        return Emulator.getRandom().nextInt(2) == 1 ? 19 : 27;
    }

    private int randomGnomeSkinColor() {
        return skins[Emulator.getRandom().nextInt(skins.length)];
    }

    public boolean deletePet(Pet pet) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("DELETE FROM users_pets WHERE id = ? LIMIT 1")) {
            statement.setInt(1, pet.getId());
            return statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return false;
    }
}
