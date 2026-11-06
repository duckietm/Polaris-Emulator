package polaris.hotel.modtool;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;

public class ScripterManager {
    public static void scripterDetected(GameClient client, String reason) {
        ScripterEvent scripterEvent = new ScripterEvent(client.getUser(), reason);
        Emulator.getPluginManager().fireEvent(scripterEvent);

        if (scripterEvent.isCancelled()) return;

        if (Emulator.getConfig().getBoolean("scripter.modtool.tickets", true)) {
            Emulator.getGameEnvironment().getModToolManager().quickTicket(client.getUser(), "Scripter", reason);
        }
    }
}
