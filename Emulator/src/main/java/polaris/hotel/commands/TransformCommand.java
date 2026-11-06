package polaris.hotel.commands;

import java.util.ArrayList;
import java.util.Collections;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.pets.PetData;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.rooms.RoomUnitType;
import polaris.messages.outgoing.generic.alerts.MessagesForYouComposer;
import polaris.messages.outgoing.rooms.users.RoomUserPetComposer;
import polaris.messages.outgoing.rooms.users.RoomUserRemoveComposer;

public class TransformCommand extends Command {
    protected TransformCommand() {
        super("cmd_transform", new String[] {"transform"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length == 1) {
            StringBuilder petNames = new StringBuilder();
            petNames.append(Emulator.getTexts().getValue("commands.generic.cmd_transform.title"));
            petNames.append("\r------------------------------------------------------------------------------\r");
            ArrayList<PetData> petData = new ArrayList<>(
                    Emulator.getGameEnvironment().getPetManager().getPetData());
            Collections.sort(petData);
            String line = Emulator.getTexts().getValue("commands.generic.cmd_transform.line");
            for (PetData p : petData) {
                petNames.append(line.replace("%id%", p.getType() + "").replace("%name%", p.getName()))
                        .append("\r");
            }

            gameClient.sendResponse(new MessagesForYouComposer(new String[] {petNames.toString()}));
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
                RoomUnit roomUnit = gameClient.getUser().getRoomUnit();
                roomUnit.setRoomUnitType(RoomUnitType.PET);
                gameClient.getUser().getUserStats().cache.put("pet_type", petData);
                gameClient.getUser().getUserStats().cache.put("pet_race", race);
                gameClient.getUser().getUserStats().cache.put("pet_color", color);
                gameClient
                        .getUser()
                        .getUserInfo()
                        .getCurrentRoom()
                        .sendComposer(
                                new RoomUserRemoveComposer(gameClient.getUser().getRoomUnit()).compose());
                gameClient
                        .getUser()
                        .getUserInfo()
                        .getCurrentRoom()
                        .sendComposer(new RoomUserPetComposer(petData.getType(), race, color, gameClient.getUser())
                                .compose());
            } else {
                // Pet Not Found
                return true;
            }
            return true;
        }
    }
}
