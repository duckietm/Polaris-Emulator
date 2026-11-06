package polaris.messages.incoming.rooms.pets;

import polaris.Emulator;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.pets.HorsePet;
import polaris.hotel.pets.MonsterplantPet;
import polaris.hotel.pets.Pet;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnitStatus;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.items.RemoveFloorItemComposer;
import polaris.messages.outgoing.rooms.pets.PetStatusUpdateComposer;
import polaris.messages.outgoing.rooms.pets.RoomPetHorseFigureComposer;
import polaris.messages.outgoing.rooms.users.RoomUserStatusComposer;
import polaris.threading.runnables.QueryDeleteUserItem;

public class PetUseItemEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());

        Room room = this.client.getUser().getUserInfo().getCurrentRoom();
        if (room == null) return;

        UserItem item = this.client.getUser().getUserInfo().getCurrentRoom().getUserItem(itemId);

        if (item == null) return;

        int petId = this.packet.readInt();
        Pet pet = this.client.getUser().getUserInfo().getCurrentRoom().getPet(petId);

        if (pet == null
                || item.getUserId() != this.client.getUser().getUserInfo().getId()
                || pet.getUserId() != this.client.getUser().getUserInfo().getId()) {
            return;
        }

        if (pet instanceof HorsePet) {
            this.useOnHorse(room, item, (HorsePet) pet);
        } else if (pet instanceof MonsterplantPet) {
            this.useOnMonsterplant(room, item, (MonsterplantPet) pet);
        }
    }

    /** A dye, hair dye, hair style or saddle changes the horse's look and is used up. */
    private void useOnHorse(Room room, UserItem item, HorsePet horse) {
        // needsUpdate also means "not saved yet": a horse that was scratched or fed carries it, and any furni used on
        // it was then taken and deleted. Only a horse item that changed the horse is used up.
        if (!applyHorseItem(item, horse)) return;

        // The item is claimed before the room hears about it: a pickup that won the race keeps the item, and the
        // horse keeps the look it was just given, so the item is never both used and back in an inventory.
        if (!room.claimItem(item)) return;

        Emulator.getThreading().run(horse);
        room.sendComposer(new RoomPetHorseFigureComposer(horse).compose());
        room.sendComposer(new RemoveFloorItemComposer(item, true).compose());
        // As a plant product does: the tiles under the used furni lose its height and its blocking.
        freeTiles(room, item);
        item.setRoomId(0);
        Emulator.getGameEnvironment().getItemManager().deleteItem(item);
    }

    /** Applies a horse item to the horse; false when the furni is not one. */
    private static boolean applyHorseItem(UserItem item, HorsePet horse) {
        String name = item.getBaseItem().getName().toLowerCase();

        // A dye, hair dye or hair style carries its number as the third part of its name; one without a readable
        // number used to throw out of the handler, now it is not a horse item.
        if (name.startsWith("horse_dye") || name.startsWith("horse_hairdye") || name.startsWith("horse_hairstyle")) {
            if (numberSuffix(name) == null) return false;
        }

        if (name.startsWith("horse_dye")) {
            int race = numberSuffix(name);
            int raceType = (race * 4) - 2;

            if (race >= 13 && race <= 17) raceType = ((2 + race) * 4) + 1;

            if (race == 0) raceType = 0;

            horse.setRace(raceType);
            horse.needsUpdate = true;
        } else if (name.startsWith("horse_hairdye")) {
            int splittedHairdye = numberSuffix(name);
            int newHairdye = 48;

            if (splittedHairdye == 0) {
                newHairdye = -1;
            } else if (splittedHairdye == 1) {
                newHairdye = 1;
            } else if (splittedHairdye >= 13 && splittedHairdye <= 17) {
                newHairdye = 68 + splittedHairdye;
            } else {
                newHairdye += splittedHairdye;
            }

            horse.setHairColor(newHairdye);
            horse.needsUpdate = true;
        } else if (name.startsWith("horse_hairstyle")) {
            int splittedHairstyle = numberSuffix(name);
            int newHairstyle = 100;

            if (splittedHairstyle == 0) {
                newHairstyle = -1;
            } else {
                newHairstyle += splittedHairstyle;
            }

            horse.setHairStyle(newHairstyle);
            horse.needsUpdate = true;
        } else if (name.startsWith("horse_saddle")) {
            horse.hasSaddle(true);
            horse.setSaddleItemId(item.getBaseItem().getId());
            horse.needsUpdate = true;
        } else {
            return false;
        }
        return true;
    }

    private void useOnMonsterplant(Room room, UserItem item, MonsterplantPet monsterplant) {
        String name = item.getBaseItem().getName();

        if (name.equalsIgnoreCase("mnstr_revival")) {
            this.revive(room, item, monsterplant);
        } else if (name.equalsIgnoreCase("mnstr_fert")) {
            this.fertilise(room, item, monsterplant);
        } else if (name.startsWith("mnstr_rebreed")) {
            this.rebreed(room, item, monsterplant);
        }
    }

    /** A revival potion brings a dead plant back. */
    private void revive(Room room, UserItem item, MonsterplantPet monsterplant) {
        if (!monsterplant.isDead()) return;
        // Claimed before it is used: a pickup racing the use left the product in an inventory while the plant had
        // already used it.
        if (!room.claimItem(item)) return;

        // Use revive() method which properly resets hasDied flag and sets needsUpdate
        monsterplant.revive();
        monsterplant.getRoomUnit().clearStatus();
        monsterplant.getRoomUnit().setStatus(RoomUnitStatus.GESTURE, "rev");
        monsterplant.packetUpdate = true;

        // Persist to database
        Emulator.getThreading().run(monsterplant);

        this.takeUsedItem(room, item, monsterplant);
        AchievementManager.progressAchievement(
                this.client.getUser(),
                Emulator.getGameEnvironment().getAchievementManager().getAchievement("MonsterPlantHealer"));
        monsterplant.getRoomUnit().removeStatus(RoomUnitStatus.GESTURE);
        Emulator.getThreading().runPersistence(new QueryDeleteUserItem(item.getId()));
    }

    /** A fertiliser takes a growing plant one growth time further. */
    private void fertilise(Room room, UserItem item, MonsterplantPet monsterplant) {
        if (monsterplant.isFullyGrown()) return;
        // Claimed before it is used: a pickup racing the use left the product in an inventory while the plant had
        // already used it.
        if (!room.claimItem(item)) return;

        monsterplant.setCreated(monsterplant.getCreated() - MonsterplantPet.growTime);
        monsterplant.needsUpdate = true;
        monsterplant.getRoomUnit().clearStatus();
        monsterplant.cycle();
        monsterplant.getRoomUnit().setStatus(RoomUnitStatus.GESTURE, "spd");
        monsterplant.getRoomUnit().setStatus(RoomUnitStatus.fromString("grw" + monsterplant.getGrowthStage()), "");

        // Persist to database
        Emulator.getThreading().run(monsterplant);

        this.takeUsedItem(room, item, monsterplant);
        monsterplant.getRoomUnit().removeStatus(RoomUnitStatus.GESTURE);
        monsterplant.cycle();
        Emulator.getThreading().runPersistence(new QueryDeleteUserItem(item.getId()));
    }

    /** A rebreed potion of the plant's rarity lets a grown plant that already bred breed again. */
    private void rebreed(Room room, UserItem item, MonsterplantPet monsterplant) {
        if (!monsterplant.isFullyGrown() || monsterplant.canBreed() || monsterplant.isDead()) return;

        String name = item.getBaseItem().getName();
        boolean validItem = (name.equalsIgnoreCase("mnstr_rebreed") && monsterplant.getRarity() <= 5)
                || (name.equalsIgnoreCase("mnstr_rebreed_2")
                        && monsterplant.getRarity() >= 6
                        && monsterplant.getRarity() <= 8)
                || (name.equalsIgnoreCase("mnstr_rebreed_3") && monsterplant.getRarity() >= 9);

        if (!validItem) return;
        // Claimed before it is used: a pickup racing the use left the product in an inventory while the plant had
        // already used it.
        if (!room.claimItem(item)) return;

        // setCanBreed now automatically sets needsUpdate
        monsterplant.setCanBreed(true);
        monsterplant.getRoomUnit().clearStatus();
        monsterplant.getRoomUnit().setStatus(RoomUnitStatus.GESTURE, "reb");

        // Persist to database
        Emulator.getThreading().run(monsterplant);

        this.takeUsedItem(room, item, monsterplant);
        monsterplant.getRoomUnit().removeStatus(RoomUnitStatus.GESTURE);
        Emulator.getThreading().runPersistence(new QueryDeleteUserItem(item.getId()));
    }

    /** Takes a used plant product out of the room, shows the plant's new state and frees the tiles under it. */
    private void takeUsedItem(Room room, UserItem item, Pet pet) {
        room.sendComposer(new RemoveFloorItemComposer(item).compose());
        room.sendComposer(new RoomUserStatusComposer(pet.getRoomUnit()).compose());
        room.sendComposer(new PetStatusUpdateComposer(pet).compose());
        freeTiles(room, item);
    }

    /** The number after the second "_" of a furni name ("horse_dye_3" gives 3), or null when there is none. */
    private static Integer numberSuffix(String name) {
        String[] parts = name.split("_");
        if (parts.length < 3) return null;
        try {
            return Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void freeTiles(Room room, UserItem item) {
        room.updateTiles(room.getLayout()
                .getTilesAt(
                        room.getLayout().getTile(item.getX(), item.getY()),
                        item.getBaseItem().getWidth(),
                        item.getBaseItem().getLength(),
                        item.getRotation()));
    }
}
