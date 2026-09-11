package com.eu.habbo.habbohotel.pets;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.rooms.pets.PetStatusUpdateComposer;
import com.eu.habbo.messages.outgoing.rooms.pets.RoomPetRespectComposer;
import com.eu.habbo.messages.outgoing.rooms.pets.breeding.MonsterplantBreedingResultComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.math3.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MonsterplantPet extends Pet implements IPetLook {
    private static final Logger LOGGER = LoggerFactory.getLogger(MonsterplantPet.class);

    public static final Map<Integer, Pair<String, Integer>> bodyRarity =
            new LinkedHashMap<Integer, Pair<String, Integer>>() {
                {
                    this.put(1, new Pair<>("Blungon", 0));
                    this.put(2, new Pair<>("Wailzor", 1));
                    this.put(3, new Pair<>("Stumpy", 1));
                    this.put(4, new Pair<>("Sunspike", 2));
                    this.put(5, new Pair<>("Squarg", 0));
                    this.put(6, new Pair<>("Shroomer", 3));
                    this.put(7, new Pair<>("Zuchinu", 3));
                    this.put(8, new Pair<>("Abysswirl", 5));
                    this.put(9, new Pair<>("Weggylum", 2));
                    this.put(10, new Pair<>("Wystique", 4));
                    this.put(11, new Pair<>("Hairbullis", 4));
                    this.put(12, new Pair<>("Snozzle", 5)); // Rarity???
                }
            };
    public static final Map<Integer, Pair<String, Integer>> colorRarity =
            new LinkedHashMap<Integer, Pair<String, Integer>>() {
                {
                    this.put(0, new Pair<>("Aenueus", 0));
                    this.put(1, new Pair<>("Griseus", 1));
                    this.put(2, new Pair<>("Phoenicus", 2));
                    this.put(3, new Pair<>("Viridulus", 1));
                    this.put(4, new Pair<>("Cyaneus", 5));
                    this.put(5, new Pair<>("Incarnatus", 2));
                    this.put(6, new Pair<>("Azureus", 4));
                    this.put(7, new Pair<>("Atamasc", 4));
                    this.put(8, new Pair<>("Amethyst", 3));
                    this.put(9, new Pair<>("Fulvus", 0));
                    this.put(10, new Pair<>("Cinereus", 3));
                }
            };
    public static final ArrayList<Pair<String, Integer>> indexedBody =
            new ArrayList<>(MonsterplantPet.bodyRarity.values());
    public static final ArrayList<Pair<String, Integer>> indexedColors =
            new ArrayList<>(MonsterplantPet.colorRarity.values());
    public static int growTime = (30 * 60);
    public static int timeToLive = (3 * 24 * 60 * 60); // 3 days
    private final int nose;
    private final int noseColor;
    private final int eyes;
    private final int eyesColor;
    private final int mouth;
    private final int mouthColor;
    public String look;
    private int type;
    private int hue;
    private int deathTimestamp = Emulator.getIntUnixTimestamp() + timeToLive;
    private boolean canBreed = true;
    private boolean publiclyBreedable = false;
    private int growthStage = 0;
    private boolean hasDied = false;

    public MonsterplantPet(ResultSet set) throws SQLException {
        super(set);
        this.type = set.getInt("mp_type");
        this.hue = set.getInt("mp_color");
        this.nose = set.getInt("mp_nose");
        this.noseColor = set.getInt("mp_nose_color");
        this.eyes = set.getInt("mp_eyes");
        this.eyesColor = set.getInt("mp_eyes_color");
        this.mouth = set.getInt("mp_mouth");
        this.mouthColor = set.getInt("mp_mouth_color");
        this.deathTimestamp = set.getInt("mp_death_timestamp");
        this.publiclyBreedable = set.getString("mp_allow_breed").equals("1");
        this.canBreed = set.getString("mp_breedable").equals("1");
        this.hasDied = set.getInt("mp_is_dead") == 1;
    }

    public MonsterplantPet(
            int userId,
            int type,
            int hue,
            int nose,
            int noseColor,
            int mouth,
            int mouthColor,
            int eyes,
            int eyesColor) {
        super(16, 0, "", "", userId);

        this.type = type;
        this.hue = hue;
        this.nose = nose;
        this.noseColor = noseColor;
        this.mouth = mouth;
        this.mouthColor = mouthColor;
        this.eyes = eyes;
        this.eyesColor = eyesColor;
    }

    @Override
    public String getName() {
        String name = "Unknownis";

        if (colorRarity.containsKey(this.hue)) {
            name = colorRarity.get(this.hue).getKey();
        }

        if (bodyRarity.containsKey(this.type)) {
            name += " " + bodyRarity.get(this.type).getKey();
        }

        return name;
    }

    @Override
    public void run() {
        if (this.needsUpdate) {
            super.run();

            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "UPDATE users_pets SET mp_type = ?, mp_color = ?, mp_nose = ?, mp_eyes = ?, mp_mouth = ?, mp_nose_color = ?, mp_eyes_color = ?, mp_mouth_color = ?, mp_death_timestamp = ?, mp_breedable = ?, mp_allow_breed = ?, mp_is_dead = ? WHERE id = ?")) {
                statement.setInt(1, this.type);
                statement.setInt(2, this.hue);
                statement.setInt(3, this.nose);
                statement.setInt(4, this.eyes);
                statement.setInt(5, this.mouth);
                statement.setInt(6, this.noseColor);
                statement.setInt(7, this.eyesColor);
                statement.setInt(8, this.mouthColor);
                statement.setInt(9, this.deathTimestamp);
                statement.setString(10, this.canBreed ? "1" : "0");
                statement.setString(11, this.publiclyBreedable ? "1" : "0");
                statement.setInt(12, this.hasDied ? 1 : 0);
                statement.setInt(13, this.id);
                statement.execute();
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }
    }

    @Override
    public void cycle() {
        if (this.room != null && this.roomUnit != null) {
            if (this.isDead()) {
                this.roomUnit.removeStatus(RoomUnitStatus.GESTURE);

                if (!this.hasDied) {
                    AchievementManager.progressAchievement(
                            Emulator.getGameEnvironment().getHabboManager().getHabbo(this.userId),
                            Emulator.getGameEnvironment()
                                    .getAchievementManager()
                                    .getAchievement("MonsterPlantGardenOfDeath"));

                    this.hasDied = true;
                    this.needsUpdate = true;
                }

                this.roomUnit.clearStatus();
                this.roomUnit.setStatus(RoomUnitStatus.RIP, "");
                this.packetUpdate = true;
            } else {
                int difference = Emulator.getIntUnixTimestamp() - this.created + 1;
                if (difference >= growTime) {
                    this.growthStage = 7;
                    boolean clear = false;
                    for (RoomUnitStatus s : this.roomUnit.getStatusMap().keySet()) {
                        if (s.equals(RoomUnitStatus.GROW)) {
                            clear = true;
                        }
                    }

                    if (clear) {
                        this.roomUnit.clearStatus();
                        this.packetUpdate = true;
                    }
                } else {
                    int g = (int) Math.ceil(difference / (growTime / 7.0));

                    if (g > this.growthStage) {
                        this.growthStage = g;
                        this.roomUnit.clearStatus();
                        this.roomUnit.setStatus(RoomUnitStatus.fromString("grw" + this.growthStage), "");
                        this.packetUpdate = true;
                    }
                }

                if (Emulator.getRandom().nextInt(1000) < 10) {
                    super.updateGesture(Emulator.getIntUnixTimestamp());
                    this.packetUpdate = true;
                }
            }
        }

        super.cycle();
    }

    public int getType() {
        return this.type;
    }

    public int getRarity() {
        if (bodyRarity.containsKey(this.type) && colorRarity.containsKey(this.hue)) {
            return bodyRarity.get(this.type).getValue()
                    + colorRarity.get(this.hue).getValue();
        }
        return 0;
    }

    @Override
    public String getLook() {
        return "16 0 FFFFFF " + "5 "
                + "0 -1 10 "
                + "1 "
                + this.type + " " + this.hue + " " + "2 "
                + this.mouth + " " + this.mouthColor + " " + "3 "
                + this.nose + " " + this.noseColor + " " + "4 "
                + this.eyes + " " + this.eyesColor;
    }

    @Override
    public void serialize(ServerMessage message) {
        message.appendInt(this.getId());
        message.appendString(this.getName());
        message.appendInt(this.petData.getType());
        message.appendInt(this.race);
        message.appendString(this.getLook().substring(5));
        message.appendInt(this.getRarity());
        message.appendInt(5);
        message.appendInt(0);
        message.appendInt(-1);
        message.appendInt(10);
        message.appendInt(1);
        message.appendInt(this.type);
        message.appendInt(this.hue);
        message.appendInt(2);
        message.appendInt(this.mouth);
        message.appendInt(this.mouthColor);
        message.appendInt(3);
        message.appendInt(this.nose);
        message.appendInt(this.noseColor);
        message.appendInt(4);
        message.appendInt(this.eyes);
        message.appendInt(this.eyesColor);

        message.appendInt(this.growthStage);
    }

    public int remainingTimeToLive() {
        return Math.max(0, this.deathTimestamp - Emulator.getIntUnixTimestamp());
    }

    public boolean isDead() {
        return Emulator.getIntUnixTimestamp() >= this.deathTimestamp;
    }

    public void setDeathTimestamp(int deathTimestamp) {
        this.deathTimestamp = deathTimestamp;
        this.needsUpdate = true;
    }

    /**
     * Revives a dead monster plant, resetting its death timestamp and hasDied flag.
     * Call this instead of just setDeathTimestamp when reviving with mnstr_revival.
     */
    public void revive() {
        this.deathTimestamp = Emulator.getIntUnixTimestamp() + MonsterplantPet.timeToLive;
        this.hasDied = false; // Reset so achievement can trigger again if plant dies again
        this.needsUpdate = true;
    }

    public int getGrowthStage() {
        return this.growthStage;
    }

    public int remainingGrowTime() {
        if (this.growthStage == 7) {
            return 0;
        }

        return Math.max(0, growTime - (Emulator.getIntUnixTimestamp() - this.created));
    }

    public boolean isFullyGrown() {
        return this.growthStage == 7;
    }

    public boolean canBreed() {
        return this.canBreed;
    }

    public void setCanBreed(boolean canBreed) {
        this.canBreed = canBreed;
        this.needsUpdate = true;
    }

    public boolean breedable() {
        return this.isFullyGrown() && this.canBreed && !this.isDead();
    }

    public boolean isPubliclyBreedable() {
        return this.publiclyBreedable;
    }

    public void setPubliclyBreedable(boolean isPubliclyBreedable) {
        this.publiclyBreedable = isPubliclyBreedable;
        this.needsUpdate = true;
    }

    public void breed(MonsterplantPet pet) {
        // Validate both plants can breed
        if (!this.breedable() || !pet.breedable()) {
            return;
        }

        if (this.canBreed && pet.canBreed) {
            this.canBreed = false;
            this.publiclyBreedable = false;
            this.needsUpdate = true;

            pet.setCanBreed(false);
            pet.setPubliclyBreedable(false);
            // pet.needsUpdate is set by setCanBreed and setPubliclyBreedable

            // Persist changes to database
            Emulator.getThreading().run(this);
            Emulator.getThreading().run(pet);

            this.room.sendComposer(new PetStatusUpdateComposer(pet).compose());
            this.room.sendComposer(new PetStatusUpdateComposer(this).compose());

            this.getRoomUnit().setStatus(RoomUnitStatus.GESTURE, "reb");
            pet.getRoomUnit().setStatus(RoomUnitStatus.GESTURE, "reb");

            this.room.sendComposer(new RoomUserStatusComposer(this.getRoomUnit()).compose());
            this.room.sendComposer(new RoomUserStatusComposer(pet.getRoomUnit()).compose());

            this.getRoomUnit().removeStatus(RoomUnitStatus.GESTURE);
            pet.getRoomUnit().removeStatus(RoomUnitStatus.GESTURE);

            Habbo ownerOne = this.room.getHabbo(this.getUserId());
            Habbo ownerTwo = null;

            if (this.getUserId() != pet.getUserId()) {
                ownerTwo = this.room.getHabbo(pet.getUserId());
            }

            Item seedBase;

            if (this.getRarity() < 8
                    || pet.getRarity() < 8
                    || Emulator.getRandom().nextInt(100) > this.getRarity() + pet.getRarity()) {
                seedBase = Emulator.getGameEnvironment()
                        .getItemManager()
                        .getItem(Emulator.getConfig().getInt("monsterplant.seed.item_id"));
            } else {
                seedBase = Emulator.getGameEnvironment()
                        .getItemManager()
                        .getItem(Emulator.getConfig().getInt("monsterplant.seed_rare.item_id"));
            }

            if (seedBase != null) {
                boolean rare = seedBase.getId() == Emulator.getConfig().getInt("monsterplant.seed_rare.item_id");
                MonsterplantBreedingResultComposer.Seed seedOne = giveSeed(ownerOne, seedBase, this.getRarity(), rare);
                MonsterplantBreedingResultComposer.Seed seedTwo = giveSeed(ownerTwo, seedBase, pet.getRarity(), rare);

                // Both owners watch the same result window, each seeing what the other got.
                MonsterplantBreedingResultComposer result = new MonsterplantBreedingResultComposer(seedOne, seedTwo);

                if (ownerOne != null) ownerOne.getClient().sendResponse(result);
                if (ownerTwo != null) ownerTwo.getClient().sendResponse(result);
            }
        }
    }

    /** Hands one owner their seed and describes it the way the result window reads it. */
    private static MonsterplantBreedingResultComposer.Seed giveSeed(
            Habbo owner, Item seedBase, int rarity, boolean mutation) {
        if (owner == null) return MonsterplantBreedingResultComposer.Seed.NONE;

        AchievementManager.progressAchievement(
                owner, Emulator.getGameEnvironment().getAchievementManager().getAchievement("MonsterPlantBreeder"), 5);

        HabboItem seed = Emulator.getGameEnvironment()
                .getItemManager()
                .createItem(owner.getHabboInfo().getId(), seedBase, 0, 0, "");

        owner.getInventory().getItemsComponent().addItem(seed);
        owner.getClient().sendResponse(new AddHabboItemComposer(seed));
        owner.getClient().sendResponse(new InventoryRefreshComposer());

        return new MonsterplantBreedingResultComposer.Seed(
                seed, owner.getHabboInfo().getId(), owner.getHabboInfo().getUsername(), rarity, mutation);
    }

    private boolean mayScratch() {
        // Monsterplant petting is available when:
        //   ((energy / max_energy) < 0.98) = true
        // You can find the minimum deathTimestamp by solving (insert a timestamp for timestamp, solve for
        // death_timestamp):
        //   (((death_timestamp - timestamp) / 259200)) < 0.98
        // This information was found in the Habbo swf, com.sulake.habbo.ui.widget.infostand.InfoStandPetView.as
        //   this._Str_2304("pettreat", ((_local_3 / _local_4) < 0.98));
        final float energy = this.getEnergy();
        final float energyMax = this.getMaxEnergy();

        return ((energy / energyMax) < 0.98);
    }

    @Override
    public int getMaxEnergy() {
        return MonsterplantPet.timeToLive;
    }

    @Override
    public int getEnergy() {
        if (this.isDead()) {
            return 100;
        }

        return this.deathTimestamp - Emulator.getIntUnixTimestamp();
    }

    @Override
    public synchronized void scratched(Habbo habbo) {
        if (this.mayScratch()) {
            AchievementManager.progressAchievement(
                    habbo,
                    Emulator.getGameEnvironment().getAchievementManager().getAchievement("MonsterPlantTreater"),
                    5);
            this.setDeathTimestamp(Emulator.getIntUnixTimestamp() + MonsterplantPet.timeToLive);
            this.addHappiness(10);
            this.addExperience(10);
            // needsUpdate is set by setDeathTimestamp, persist to database
            Emulator.getThreading().run(this);
            this.room.sendComposer(new PetStatusUpdateComposer(this).compose());
            this.room.sendComposer(new RoomPetRespectComposer(this, RoomPetRespectComposer.PET_TREATED).compose());
        }
    }

    @Override
    public boolean canWalk() {
        return false;
    }
}
