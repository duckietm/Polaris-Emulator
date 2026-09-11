package com.eu.habbo.habbohotel.items.interactions.pets;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.pets.PetManager;
import com.eu.habbo.habbohotel.pets.PetTasks;
import com.eu.habbo.habbohotel.pets.RideablePet;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitType;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.rooms.pets.PetPackageNameValidationComposer;
import com.eu.habbo.messages.outgoing.rooms.pets.breeding.NestBreedingSuccessComposer;
import com.eu.habbo.messages.outgoing.rooms.pets.breeding.PetBreedingFailedComposer;
import com.eu.habbo.messages.outgoing.rooms.pets.breeding.PetBreedingResultComposer;
import com.eu.habbo.threading.runnables.QueryDeleteHabboItem;
import java.sql.ResultSet;
import java.sql.SQLException;

public class InteractionPetBreedingNest extends HabboItem {
    public Pet petOne = null;
    public Pet petTwo = null;

    public InteractionPetBreedingNest(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionPetBreedingNest(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        Pet pet = room.getPet(roomUnit);
        if (pet == null) return false;
        // Don't let ridden pets enter breeding nest
        if (pet instanceof RideablePet && ((RideablePet) pet).getRider() != null) return false;
        return !this.boxFull();
    }

    @Override
    public boolean isWalkable() {
        return true;
    }

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        serverMessage.appendInt((this.isLimited() ? 256 : 0));
        serverMessage.appendString(this.getExtradata());

        super.serializeExtradata(serverMessage);
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {}

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        Pet pet = room.getPet(roomUnit);

        if (pet != null) {
            if (!this.boxFull()) {
                this.addPet(pet);

                if (this.boxFull()) {
                    Habbo ownerPetOne = room.getHabbo(this.petOne.getUserId());
                    Habbo ownerPetTwo = room.getHabbo(this.petTwo.getUserId());

                    if (ownerPetOne != null
                            && ownerPetTwo != null
                            && this.petOne.getPetData().getType()
                                    == this.petTwo.getPetData().getType()
                            && this.petOne.getPetData().getOffspringType() != -1) {
                        this.requestBreedingConfirmation(room, ownerPetOne, ownerPetTwo);
                    } else {
                        this.freePets();
                    }
                }
            }
        }
    }

    public boolean addPet(Pet pet) {
        if (this.petOne == null) {
            this.petOne = pet;
            this.petOne.getRoomUnit().setCanWalk(false);
            return true;
        } else if (this.petTwo == null && this.petOne != pet) {
            this.petTwo = pet;
            this.petTwo.getRoomUnit().setCanWalk(false);
            return true;
        }

        return false;
    }

    public boolean boxFull() {
        return this.petOne != null && this.petTwo != null;
    }

    @Override
    public void onWalkOff(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        if (this.petOne != null && this.petOne.getRoomUnit() == roomUnit) this.petOne = null;
        if (this.petTwo != null && this.petTwo.getRoomUnit() == roomUnit) this.petTwo = null;

        this.setExtradata("0");
        room.updateItem(this);
    }

    @Override
    public boolean allowWiredResetState() {
        return false;
    }

    /**
     * Allow pets to walk onto this tile even if another pet is already on it.
     * This is required because breeding nests are 1x1 and need 2 pets to breed.
     */
    @Override
    public boolean canOverrideTile(RoomUnit unit, Room room, RoomTile tile) {
        // Only allow override for pets when the box isn't full yet
        if (unit.getRoomUnitType() == RoomUnitType.PET && !this.boxFull()) {
            Pet pet = room.getPet(unit);
            if (pet != null) {
                // Make sure it's the right pet type for this nest
                if (pet.getPetData() != null && pet.getPetData().getOffspringType() != -1) {
                    return true;
                }
            }
        }
        return false;
    }

    public void stopBreeding(Habbo habbo) {
        this.setExtradata("0");
        habbo.getHabboInfo().getCurrentRoom().updateItem(this);

        // Official `ConfirmBreedingResult` code 0: close the naming dialog.
        habbo.getClient().sendResponse(new PetBreedingFailedComposer(this.getId(), CONFIRM_RESULT_OK));

        if (this.petOne != null) {
            habbo.getClient()
                    .sendResponse(new PetPackageNameValidationComposer(
                            this.getId(), PetPackageNameValidationComposer.CLOSE_WIDGET, ""));
        }
        if (this.petTwo != null
                && this.petTwo.getUserId() != habbo.getHabboInfo().getId()) {
            Habbo owner =
                    this.petTwo.getRoom() != null ? this.petTwo.getRoom().getHabbo(this.petTwo.getUserId()) : null;
            if (owner != null && owner.getClient() != null) {
                owner.getClient()
                        .sendResponse(new PetPackageNameValidationComposer(
                                this.getId(), PetPackageNameValidationComposer.CLOSE_WIDGET, ""));
            }
        }

        this.freePets();
    }

    private void freePets() {
        if (this.petOne != null) {
            this.petOne.getRoomUnit().setCanWalk(true);
            this.petOne.setTask(PetTasks.FREE);
            this.petOne = null;
        }

        if (this.petTwo != null) {
            this.petTwo.getRoomUnit().setCanWalk(true);
            this.petTwo.setTask(PetTasks.FREE);
            this.petTwo = null;
        }
    }

    /**
     * Official {@code ConfirmBreedingRequest} (AIR 13, header 634 here): a full
     * nest does not breed on its own - both owners get the confirmation dialog
     * with the two parents, the four rarity buckets and their odds, and the
     * offspring only exists once someone names it
     * ({@code ConfirmPetBreedingEvent}).
     */
    private void requestBreedingConfirmation(Room room, Habbo ownerPetOne, Habbo ownerPetTwo) {
        this.setExtradata("1");
        room.updateItem(this);

        PetBreedingResultComposer request = new PetBreedingResultComposer(
                this.getId(),
                this.petOne.getPetData().getOffspringType(),
                this.petOne,
                ownerPetOne.getHabboInfo().getUsername(),
                this.petTwo,
                ownerPetTwo.getHabboInfo().getUsername());

        ownerPetOne.getClient().sendResponse(request);

        if (ownerPetTwo != ownerPetOne) {
            ownerPetTwo.getClient().sendResponse(request);
        }
    }

    private String generateBabyName(String nameOne, String nameTwo) {
        // Take first half of parent 1's name and second half of parent 2's name
        int mid1 = Math.max(1, nameOne.length() / 2);
        int mid2 = nameTwo.length() / 2;

        String part1 = nameOne.substring(0, mid1);
        String part2 = nameTwo.substring(mid2);

        String combined = part1 + part2;

        // Ensure name is between 1 and 15 characters
        if (combined.length() > 15) {
            combined = combined.substring(0, 15);
        }
        if (combined.isEmpty()) {
            combined = "Baby";
        }

        return combined;
    }

    /**
     * Official {@code ConfirmBreedingResult} (AIR 13, header 1625 here): the
     * codes {@code AvatarInfoWidget} turns into an alert - 0 closes the dialog,
     * 1 is "the nest is gone", 2 is "the pets are gone", 3 is "that name is not
     * allowed" and re-enables the naming dialog.
     */
    public static final int CONFIRM_RESULT_OK = 0;

    public static final int CONFIRM_RESULT_NO_NEST = 1;
    public static final int CONFIRM_RESULT_PETS_MISSING = 2;
    public static final int CONFIRM_RESULT_NAME_INVALID = 3;

    /** Longest offspring name the client's naming dialog accepts. */
    private static final int MAX_NAME_LENGTH = 15;

    public void breed(Habbo habbo, String name, int petOneId, int petTwoId) {
        // Guard before the destructive delete below: a crafted packet can call
        // this on a nest that isn't full, which would delete the nest furni and
        // then NPE on petOne/petTwo in the async runnable (losing the furni).
        if (habbo == null || habbo.getHabboInfo().getCurrentRoom() == null) {
            return;
        }

        if (this.petOne == null || this.petTwo == null) {
            habbo.getClient().sendResponse(new PetBreedingFailedComposer(this.getId(), CONFIRM_RESULT_PETS_MISSING));
            return;
        }

        if (this.petOne.getId() != petOneId && this.petOne.getId() != petTwoId) {
            habbo.getClient().sendResponse(new PetBreedingFailedComposer(this.getId(), CONFIRM_RESULT_PETS_MISSING));
            return;
        }

        String trimmedName = name == null ? "" : name.trim();
        if (trimmedName.isEmpty() || trimmedName.length() > MAX_NAME_LENGTH) {
            habbo.getClient().sendResponse(new PetBreedingFailedComposer(this.getId(), CONFIRM_RESULT_NAME_INVALID));
            return;
        }

        habbo.getClient().sendResponse(new PetBreedingFailedComposer(this.getId(), CONFIRM_RESULT_OK));

        Emulator.getThreading().runPersistence(new QueryDeleteHabboItem(this.getId()));

        this.setExtradata("2");
        habbo.getHabboInfo().getCurrentRoom().updateItem(this);

        final String offspringName = trimmedName;
        HabboItem box = this;
        Pet petOne = this.petOne;
        Pet petTwo = this.petTwo;
        Emulator.getThreading()
                .run(
                        () -> {
                            Pet offspring = Emulator.getGameEnvironment()
                                    .getPetManager()
                                    .createPet(
                                            petOne.getPetData().getOffspringType(),
                                            (int) Math.min(
                                                    Math.round(Math.max(
                                                            1d,
                                                            PetManager.getNormalDistributionForBreeding(
                                                                            petOne.getLevel(), petTwo.getLevel())
                                                                    .sample())),
                                                    20),
                                            offspringName,
                                            habbo.getClient());

                            // habbo.getClient().sendResponse(new PetPackageNameValidationComposer(box.getId(),
                            // PetPackageNameValidationComposer.CLOSE_WIDGET, ""));
                            habbo.getHabboInfo()
                                    .getCurrentRoom()
                                    .placePet(offspring, box.getX(), box.getY(), box.getZ(), box.getRotation());
                            offspring.needsUpdate = true;
                            offspring.run();
                            InteractionPetBreedingNest.this.freePets();
                            habbo.getHabboInfo().getCurrentRoom().removeHabboItem(box);
                            // Official `NestBreedingSuccess`: the success dialog needs the
                            // offspring and the rarity bucket its breed landed in.
                            habbo.getClient()
                                    .sendResponse(new NestBreedingSuccessComposer(
                                            offspring.getId(),
                                            Emulator.getGameEnvironment()
                                                    .getPetManager()
                                                    .getRarityForOffspring(offspring)));

                            if (box.getBaseItem().getName().startsWith("pet_breeding_")) {
                                String boxType = box.getBaseItem().getName().replace("pet_breeding_", "");
                                String achievement =
                                        boxType.substring(0, 1).toUpperCase() + boxType.substring(1) + "Breeder";
                                AchievementManager.progressAchievement(
                                        habbo,
                                        Emulator.getGameEnvironment()
                                                .getAchievementManager()
                                                .getAchievement(achievement));
                            }
                        },
                        2000);
    }
}
