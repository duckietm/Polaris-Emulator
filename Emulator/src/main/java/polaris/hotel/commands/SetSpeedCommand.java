package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomChatMessageBubbles;

public class SetSpeedCommand extends Command {
    public SetSpeedCommand() {
        super(
                "cmd_setspeed",
                Emulator.getTexts().getValue("commands.keys.cmd_setspeed").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (gameClient.getUser().getUserInfo().getCurrentRoom() != null) {
            if (gameClient.getUser().getUserInfo().getCurrentRoom().hasRights(gameClient.getUser())) {
                Room room = gameClient.getUser().getUserInfo().getCurrentRoom();

                int oldSpeed = room.getRollerSpeed();
                int newSpeed;

                try {
                    newSpeed = Integer.parseInt(params[1]);
                } catch (Exception e) {
                    gameClient
                            .getUser()
                            .whisper(
                                    Emulator.getTexts().getValue("commands.error.cmd_setspeed.invalid_amount"),
                                    RoomChatMessageBubbles.ALERT);
                    return true;
                }

                if (newSpeed < -1 || newSpeed > Emulator.getConfig().getInt("hotel.rollers.speed.maximum")) {
                    gameClient
                            .getUser()
                            .whisper(
                                    Emulator.getTexts().getValue("commands.error.cmd_setspeed.bounds"),
                                    RoomChatMessageBubbles.ALERT);
                    return true;
                }

                room.setRollerSpeed(newSpeed);

                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts()
                                        .getValue("commands.succes.cmd_setspeed")
                                        .replace("%oldspeed%", oldSpeed + "")
                                        .replace("%newspeed%", newSpeed + ""),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }
        }
        return false;
    }
}
