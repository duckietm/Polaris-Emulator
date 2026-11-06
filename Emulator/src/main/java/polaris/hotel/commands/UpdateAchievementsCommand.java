package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;

public class UpdateAchievementsCommand extends Command {
    public UpdateAchievementsCommand() {
        super(
                "cmd_update_achievements",
                Emulator.getTexts()
                        .getValue("commands.keys.cmd_update_achievements")
                        .split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        Emulator.getGameEnvironment().getAchievementManager().reload();
        gameClient
                .getUser()
                .whisper(
                        Emulator.getTexts().getValue("commands.succes.cmd_update_achievements.updated"),
                        RoomChatMessageBubbles.ALERT);
        return true;
    }
}
