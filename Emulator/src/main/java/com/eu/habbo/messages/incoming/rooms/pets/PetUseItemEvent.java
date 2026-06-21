package com.eu.habbo.messages.incoming.rooms.pets;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.pets.HorsePet;
import com.eu.habbo.habbohotel.pets.MonsterplantPet;
import com.eu.habbo.habbohotel.pets.Pet;
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
        int itemId = this.packet.readInt();

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null)
            return;

        HabboItem item = this.client.getHabbo().getHabboInfo().getCurrentRoom().getHabboItem(itemId);

        if (item == null)
            return;

        int petId = this.packet.readInt();
        Pet pet = this.client.getHabbo().getHabboInfo().getCurrentRoom().getPet(petId);

        if (pet == null
                || item.getUserId() != this.client.getHabbo().getHabboInfo().getId()
                || pet.getUserId() != this.client.getHabbo().getHabboInfo().getId()) {
            return;
        }

        if (pet instanceof HorsePet) {
            if (item.getBaseItem().getName().toLowerCase().startsWith("horse_dye")) {
                int race = Integer.parseInt(item.getBaseItem().getName().split("_")[2]);
                int raceType = (race * 4) - 2;

                if (race >= 13 && race <= 17)
                    raceType = ((2 + race) * 4) + 1;

                if (race == 0)
                    raceType = 0;

                pet.setRace(raceType);
                ((HorsePet) pet).needsUpdate = true;
            } else if (item.getBaseItem().getName().toLowerCase().startsWith("horse_hairdye")) {
                int splittedHairdye = Integer.parseInt(item.getBaseItem().getName().toLowerCase().split("_")[2]);
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

                ((HorsePet) pet).setHairColor(newHairdye);
                ((HorsePet) pet).needsUpdate = true;
            } else if (item.getBaseItem().getName().toLowerCase().startsWith("horse_hairstyle")) {
                int splittedHairstyle = Integer.parseInt(item.getBaseItem().getName().toLowerCase().split("_")[2]);
                int newHairstyle = 100;

                if (splittedHairstyle == 0) {
                    newHairstyle = -1;
                } else {
                    newHairstyle += splittedHairstyle;
                }

                ((HorsePet) pet).setHairStyle(newHairstyle);
                ((HorsePet) pet).needsUpdate = true;
            } else if (item.getBaseItem().getName().toLowerCase().startsWith("horse_saddle")) {
                ((HorsePet) pet).hasSaddle(true);
                ((HorsePet) pet).setSaddleItemId(item.getBaseItem().getId());
                ((HorsePet) pet).needsUpdate = true;
            }

            if (((HorsePet) pet).needsUpdate) {
                Emulator.getThreading().run(pet);
                this.client.getHabbo().getHabboInfo().getCurrentRoom().sendComposer(new RoomPetHorseFigureComposer((HorsePet) pet).compose());

                room.removeHabboItem(item);
                room.sendComposer(new RemoveFloorItemComposer(item, true).compose());
                item.setRoomId(0);
                Emulator.getGameEnvironment().getItemManager().deleteItem(item);
            }
        } else if (pet instanceof MonsterplantPet) {
            MonsterplantPet monsterplant = (MonsterplantPet) pet;
            
            if (item.getBaseItem().getName().equalsIgnoreCase("mnstr_revival")) {
                if (monsterplant.isDead()) {
                    // Use revive() method which properly resets hasDied flag and sets needsUpdate
                    monsterplant.revive();
                    pet.getRoomUnit().clearStatus();
                    pet.getRoomUnit().setStatus(RoomUnitStatus.GESTURE, "rev");
                    monsterplant.packetUpdate = true;
                    
                    // Persist to database
                    Emulator.getThreading().run(pet);

                    this.client.getHabbo().getHabboInfo().getCurrentRoom().removeHabboItem(item);
                    this.client.getHabbo().getHabboInfo().getCurrentRoom().sendComposer(new RemoveFloorItemComposer(item).compose());
                    this.client.getHabbo().getHabboInfo().getCurrentRoom().sendComposer(new RoomUserStatusComposer(pet.getRoomUnit()).compose());
                    this.client.getHabbo().getHabboInfo().getCurrentRoom().sendComposer(new PetStatusUpdateComposer(pet).compose());
                    this.client.getHabbo().getHabboInfo().getCurrentRoom().updateTiles(room.getLayout().getTilesAt(room.getLayout().getTile(item.getX(), item.getY()), item.getBaseItem().getWidth(), item.getBaseItem().getLength(), item.getRotation()));
                    AchievementManager.progressAchievement(this.client.getHabbo(), Emulator.getGameEnvironment().getAchievementManager().getAchievement("MonsterPlantHealer"));
                    pet.getRoomUnit().removeStatus(RoomUnitStatus.GESTURE);
                    Emulator.getThreading().run(new QueryDeleteHabboItem(item.getId()));
                }
            } else if (item.getBaseItem().getName().equalsIgnoreCase("mnstr_fert")) {
                if (!monsterplant.isFullyGrown()) {
                    pet.setCreated(pet.getCreated() - MonsterplantPet.growTime);
                    pet.needsUpdate = true;
                    pet.getRoomUnit().clearStatus();
                    pet.cycle();
                    pet.getRoomUnit().setStatus(RoomUnitStatus.GESTURE, "spd");
                    pet.getRoomUnit().setStatus(RoomUnitStatus.fromString("grw" + monsterplant.getGrowthStage()), "");
                    
                    // Persist to database
                    Emulator.getThreading().run(pet);
                    
                    this.client.getHabbo().getHabboInfo().getCurrentRoom().removeHabboItem(item);
                    this.client.getHabbo().getHabboInfo().getCurrentRoom().sendComposer(new RemoveFloorItemComposer(item).compose());
                    this.client.getHabbo().getHabboInfo().getCurrentRoom().sendComposer(new RoomUserStatusComposer(pet.getRoomUnit()).compose());
                    this.client.getHabbo().getHabboInfo().getCurrentRoom().sendComposer(new PetStatusUpdateComposer(pet).compose());
                    this.client.getHabbo().getHabboInfo().getCurrentRoom().updateTiles(room.getLayout().getTilesAt(room.getLayout().getTile(item.getX(), item.getY()), item.getBaseItem().getWidth(), item.getBaseItem().getLength(), item.getRotation()));
                    pet.getRoomUnit().removeStatus(RoomUnitStatus.GESTURE);
                    pet.cycle();
                    Emulator.getThreading().run(new QueryDeleteHabboItem(item.getId()));
                }
            } else if (item.getBaseItem().getName().startsWith("mnstr_rebreed")) {
                if (monsterplant.isFullyGrown() && !monsterplant.canBreed() && !monsterplant.isDead()) {
                    boolean validItem = 
                            (item.getBaseItem().getName().equalsIgnoreCase("mnstr_rebreed") && monsterplant.getRarity() <= 5) ||
                            (item.getBaseItem().getName().equalsIgnoreCase("mnstr_rebreed_2") && monsterplant.getRarity() >= 6 && monsterplant.getRarity() <= 8) ||
                            (item.getBaseItem().getName().equalsIgnoreCase("mnstr_rebreed_3") && monsterplant.getRarity() >= 9);

                    if (validItem) {
                        // setCanBreed now automatically sets needsUpdate
                        monsterplant.setCanBreed(true);
                        pet.getRoomUnit().clearStatus();
                        pet.getRoomUnit().setStatus(RoomUnitStatus.GESTURE, "reb");
                        
                        // Persist to database
                        Emulator.getThreading().run(pet);

                        this.client.getHabbo().getHabboInfo().getCurrentRoom().removeHabboItem(item);
                        this.client.getHabbo().getHabboInfo().getCurrentRoom().sendComposer(new RemoveFloorItemComposer(item).compose());
                        this.client.getHabbo().getHabboInfo().getCurrentRoom().sendComposer(new RoomUserStatusComposer(pet.getRoomUnit()).compose());
                        this.client.getHabbo().getHabboInfo().getCurrentRoom().sendComposer(new PetStatusUpdateComposer(pet).compose());
                        this.client.getHabbo().getHabboInfo().getCurrentRoom().updateTiles(room.getLayout().getTilesAt(room.getLayout().getTile(item.getX(), item.getY()), item.getBaseItem().getWidth(), item.getBaseItem().getLength(), item.getRotation()));
                        pet.getRoomUnit().removeStatus(RoomUnitStatus.GESTURE);
                        Emulator.getThreading().run(new QueryDeleteHabboItem(item.getId()));
                    }
                }
            }
        }
    }
}
