package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.pets.PetManager;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;

public class PetInfoCommand extends Command {
    public PetInfoCommand() {
        super("cmd_pet_info", Emulator.getTexts().getValue("commands.keys.cmd_pet_info").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length > 1) {
            if (gameClient.getHabbo().getHabboInfo().getCurrentRoom() == null)
                return false;

            String name = params[1];

            for (Pet pet : gameClient.getHabbo().getHabboInfo().getCurrentRoom().getCurrentPets().values()) {
                if (pet.getName().equalsIgnoreCase(name)) {
                    gameClient.getHabbo().alert("" +
                            Emulator.getTexts().getValue("commands.generic.cmd_pet_info.title") + ": " + pet.getName() + "\r\n" +
                            Emulator.getTexts().getValue("generic.pet.id") + ": " + pet.getId() + "\r" +
                            Emulator.getTexts().getValue("generic.pet.name") + ": " + pet.getName() + "\r" +
                            Emulator.getTexts().getValue("generic.pet.age") + ": " + pet.daysAlive() + " " + Emulator.getTexts().getValue("generic.pet.days.alive") + "\r" +
                            Emulator.getTexts().getValue("generic.pet.level") + ": " + pet.getLevel() + "\r" +
                            "\r" +
                            Emulator.getTexts().getValue("commands.generic.cmd_pet_info.stats") + "\r\n" +
                            Emulator.getTexts().getValue("generic.pet.scratches") + ": " + pet.getRespect() + "\r" +
                            Emulator.getTexts().getValue("generic.pet.energy") + ": " + pet.getEnergy() + "/" + PetManager.maxEnergy(pet.getLevel()) + "\r" +
                            Emulator.getTexts().getValue("generic.pet.happiness") + ": " + pet.getHappiness() + "\r" +
                            Emulator.getTexts().getValue("generic.pet.level.thirst") + ": " + pet.levelThirst + "\r" +
                            Emulator.getTexts().getValue("generic.pet.level.hunger") + ": " + pet.levelHunger + "\r" +
                            Emulator.getTexts().getValue("generic.pet.current_action") + ": " + (pet.getTask() == null ? Emulator.getTexts().getValue("generic.nothing") : pet.getTask().name()) + "\r" +
                            Emulator.getTexts().getValue("generic.can.walk") + ": " + (pet.getRoomUnit().canWalk() ? Emulator.getTexts().getValue("generic.yes") : Emulator.getTexts().getValue("generic.no")) + ""
                    );
                }
            }
        } else {
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_pet_info.pet_not_found"), RoomChatMessageBubbles.ALERT);
        }
        return true;
    }
}
