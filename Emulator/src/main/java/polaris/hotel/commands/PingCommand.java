package polaris.hotel.commands;

import polaris.hotel.LatencyTracker;
import polaris.hotel.gameclients.GameClient;

public class PingCommand extends Command {
    public PingCommand() {
        super(null, new String[] {"ping"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (gameClient.getUser().getRoomUnit() == null) {
            return true;
        }

        final LatencyTracker latencyTracker = gameClient.getLatencyTracker();

        if (latencyTracker.hasInitialized()) {
            gameClient
                    .getUser()
                    .whisper(String.format(
                            "Average ping %dms, last ping %dms",
                            latencyTracker.getAverageMs(), latencyTracker.getLastMs()));
        } else {
            gameClient
                    .getUser()
                    .whisper("\n" + "Ping speed has not been calculated yet, please try again in a minute.");
        }

        return true;
    }
}
