package polaris.hotel.commands;

import java.util.Map;
import polaris.Emulator;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.users.User;
import polaris.messages.outgoing.generic.alerts.GenericAlertComposer;

public class HappyHourCommand extends Command {
    public HappyHourCommand() {
        super(
                "cmd_happyhour",
                Emulator.getTexts().getValue("commands.keys.cmd_happyhour").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        Emulator.getGameServer().getGameClientManager().sendBroadcastResponse(new GenericAlertComposer("Happy Hour!"));

        for (Map.Entry<Integer, User> set :
                Emulator.getGameEnvironment().getUserManager().getOnlineUsers().entrySet()) {
            AchievementManager.progressAchievement(
                    set.getValue(),
                    Emulator.getGameEnvironment().getAchievementManager().getAchievement("HappyHour"));
        }

        return true;
    }
}
