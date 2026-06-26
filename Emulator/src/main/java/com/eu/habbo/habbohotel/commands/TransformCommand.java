package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.RoomUserPetComposer;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.pets.PetData;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitType;
import com.eu.habbo.messages.outgoing.generic.alerts.MessagesForYouComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserRemoveComposer;

import java.util.ArrayList;
import java.util.Collections;

public class TransformCommand extends Command {
    protected TransformCommand() {
        super("cmd_transform", new String[]{"transform"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length == 1) {
            var petNames = new StringBuilder();
            petNames.append(Emulator.getTexts().getValue("commands.generic.cmd_transform.title"));
            petNames.append("\r------------------------------------------------------------------------------\r");
            var petData = new ArrayList<PetData>(Emulator.getGameEnvironment().getPetManager().getPetData());
            Collections.sort(petData);
            String line = Emulator.getTexts().getValue("commands.generic.cmd_transform.line");
            for (PetData p : petData) {
                petNames.append(line.replace("%id%", p.getType() + "").replace("%name%", p.getName())).append("\r");
            }

            gameClient.sendResponse(new MessagesForYouComposer(new String[]{petNames.toString()}));
            return true;
        } else {
            String petName = params[1];
            PetData petData = Emulator.getGameEnvironment().getPetManager().getPetData(petName);

            int race = 0;

            if (params.length >= 3) {
                try {
                    race = Integer.parseInt(params[2]);
                } catch (Exception e) {
                    return true;
                }
            }

            String color = "FFFFFF";
            if (params.length >= 4) {
                color = params[3];
            }

            if (petData != null) {
                RoomUnit roomUnit = gameClient.getHabbo().getRoomUnit();
                roomUnit.setRoomUnitType(RoomUnitType.PET);
                gameClient.getHabbo().getHabboStats().cache.put("pet_type", petData);
                gameClient.getHabbo().getHabboStats().cache.put("pet_race", race);
                gameClient.getHabbo().getHabboStats().cache.put("pet_color", color);
                gameClient.getHabbo().getHabboInfo().getCurrentRoom().sendComposer(new RoomUserRemoveComposer(gameClient.getHabbo().getRoomUnit()).compose());
                gameClient.getHabbo().getHabboInfo().getCurrentRoom().sendComposer(new RoomUserPetComposer(petData.getType(), race, color, gameClient.getHabbo()).compose());
            } else {
                //Pet Not Found
                return true;
            }
            return true;
        }
    }
}