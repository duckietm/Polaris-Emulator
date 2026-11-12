package polaris.hotel.commands;

import polaris.Emulator;
import polaris.core.TextsManager;
import polaris.hotel.LatencyTracker;
import polaris.hotel.gameclients.GameClient;

/** {@code :ping}: whispers the player the round trip the server measured for their own connection. */
public class PingCommand extends Command {
    public PingCommand() {
        super("cmd_ping", texts().getValue("commands.keys.cmd_ping", "ping").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (gameClient.getUser().getRoomUnit() == null) {
            return true;
        }

        final LatencyTracker latencyTracker = gameClient.getLatencyTracker();

        gameClient.getUser().whisper(message(texts(), latencyTracker));

        return true;
    }

    static String message(TextsManager texts, LatencyTracker latencyTracker) {
        if (!latencyTracker.hasInitialized()) {
            return texts.getValue(
                    "commands.error.cmd_ping.not_ready",
                    "Your ping has not been measured yet, please try again in a minute.");
        }

        return texts.getValue("commands.succes.cmd_ping", "Average ping %average%ms, last ping %last%ms")
                .replace("%average%", Long.toString(latencyTracker.getAverageMs()))
                .replace("%last%", Long.toString(latencyTracker.getLastMs()));
    }

    private static TextsManager texts() {
        return Emulator.getTexts();
    }
}
