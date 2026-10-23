package com.eu.habbo.messages.incoming.rooms.pets;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.pets.HorsePet;
import com.eu.habbo.habbohotel.pets.MonsterplantPet;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.items.RemoveFloorItemComposer;
import com.eu.habbo.messages.outgoing.rooms.pets.PetStatusUpdateComposer;
import com.eu.habbo.messages.outgoing.rooms.pets.RoomPetHorseFigureComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import com.eu.habbo.threading.runnables.QueryDeleteHabboItem;

public class PetUseItemEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null) return;

        HabboItem item = this.client.getHabbo().getHabboInfo().getCurrentRoom().getHabboItem(itemId);

        if (item == null) return;

        int petId = this.packet.readInt();
        Pet pet = this.client.getHabbo().getHabboInfo().getCurrentRoom().getPet(petId);

        if (pet == null
                || item.getUserId() != this.client.getHabbo().getHabboInfo().getId()
                || pet.getUserId() != this.client.getHabbo().getHabboInfo().getId()) {
            return;
        }

        if (pet instanceof HorsePet) {
            this.useOnHorse(room, item, (HorsePet) pet);
        } else if (pet instanceof MonsterplantPet) {
            this.useOnMonsterplant(room, item, (MonsterplantPet) pet);
        }
    }

    /** A dye, hair dye, hair style or saddle changes the horse's look and is used up. */
    private void useOnHorse(Room room, HabboItem item, HorsePet horse) {
        // needsUpdate also means "not saved yet": a horse that was scratched or fed carries it, and any furni used on
        // it was then taken and deleted. Only a horse item that changed the horse is used up.
        if (!applyHorseItem(item, horse)) return;

        Emulator.getThreading().run(horse);
        room.sendComposer(new RoomPetHorseFigureComposer(horse).compose());

        room.removeHabboItem(item);
        room.sendComposer(new RemoveFloorItemComposer(item, true).compose());
        item.setRoomId(0);
        Emulator.getGameEnvironment().getItemManager().deleteItem(item);
    }

    /** Applies a horse item to the horse; false when the furni is not one. */
    private static boolean applyHorseItem(HabboItem item, HorsePet horse) {
        String name = item.getBaseItem().getName().toLowerCase();

        if (name.startsWith("horse_dye")) {
            int race = Integer.parseInt(item.getBaseItem().getName().split("_")[2]);
            int raceType = (race * 4) - 2;

            if (race >= 13 && race <= 17) raceType = ((2 + race) * 4) + 1;

            if (race == 0) raceType = 0;

            horse.setRace(raceType);
            horse.needsUpdate = true;
        } else if (name.startsWith("horse_hairdye")) {
            int splittedHairdye = Integer.parseInt(name.split("_")[2]);
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
            int splittedHairstyle = Integer.parseInt(name.split("_")[2]);
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

    private void useOnMonsterplant(Room room, HabboItem item, MonsterplantPet monsterplant) {
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
    private void revive(Room room, HabboItem item, MonsterplantPet monsterplant) {
        if (!monsterplant.isDead()) return;

        // Use revive() method which properly resets hasDied flag and sets needsUpdate
        monsterplant.revive();
        monsterplant.getRoomUnit().clearStatus();
        monsterplant.getRoomUnit().setStatus(RoomUnitStatus.GESTURE, "rev");
        monsterplant.packetUpdate = true;

        // Persist to database
        Emulator.getThreading().run(monsterplant);

        this.takeUsedItem(room, item, monsterplant);
        AchievementManager.progressAchievement(
                this.client.getHabbo(),
                Emulator.getGameEnvironment().getAchievementManager().getAchievement("MonsterPlantHealer"));
        monsterplant.getRoomUnit().removeStatus(RoomUnitStatus.GESTURE);
        Emulator.getThreading().runPersistence(new QueryDeleteHabboItem(item.getId()));
    }

    /** A fertiliser takes a growing plant one growth time further. */
    private void fertilise(Room room, HabboItem item, MonsterplantPet monsterplant) {
        if (monsterplant.isFullyGrown()) return;

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
        Emulator.getThreading().runPersistence(new QueryDeleteHabboItem(item.getId()));
    }

    /** A rebreed potion of the plant's rarity lets a grown plant that already bred breed again. */
    private void rebreed(Room room, HabboItem item, MonsterplantPet monsterplant) {
        if (!monsterplant.isFullyGrown() || monsterplant.canBreed() || monsterplant.isDead()) return;

        String name = item.getBaseItem().getName();
        boolean validItem = (name.equalsIgnoreCase("mnstr_rebreed") && monsterplant.getRarity() <= 5)
                || (name.equalsIgnoreCase("mnstr_rebreed_2")
                        && monsterplant.getRarity() >= 6
                        && monsterplant.getRarity() <= 8)
                || (name.equalsIgnoreCase("mnstr_rebreed_3") && monsterplant.getRarity() >= 9);

        if (!validItem) return;

        // setCanBreed now automatically sets needsUpdate
        monsterplant.setCanBreed(true);
        monsterplant.getRoomUnit().clearStatus();
        monsterplant.getRoomUnit().setStatus(RoomUnitStatus.GESTURE, "reb");

        // Persist to database
        Emulator.getThreading().run(monsterplant);

        this.takeUsedItem(room, item, monsterplant);
        monsterplant.getRoomUnit().removeStatus(RoomUnitStatus.GESTURE);
        Emulator.getThreading().runPersistence(new QueryDeleteHabboItem(item.getId()));
    }

    /** Takes a used plant product out of the room, shows the plant's new state and frees the tiles under it. */
    private void takeUsedItem(Room room, HabboItem item, Pet pet) {
        room.removeHabboItem(item);
        room.sendComposer(new RemoveFloorItemComposer(item).compose());
        room.sendComposer(new RoomUserStatusComposer(pet.getRoomUnit()).compose());
        room.sendComposer(new PetStatusUpdateComposer(pet).compose());
        room.updateTiles(room.getLayout()
                .getTilesAt(
                        room.getLayout().getTile(item.getX(), item.getY()),
                        item.getBaseItem().getWidth(),
                        item.getBaseItem().getLength(),
                        item.getRotation()));
    }
}
