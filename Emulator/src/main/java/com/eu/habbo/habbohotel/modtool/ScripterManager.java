package com.eu.habbo.habbohotel.modtool;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;

public class ScripterManager {
    public static void scripterDetected(GameClient client, String reason) {
        var scripterEvent = new ScripterEvent(client.getHabbo(), reason);
        Emulator.getPluginManager().fireEvent(scripterEvent);

        if (scripterEvent.isCancelled()) return;

        if (Emulator.getConfig().getBoolean("scripter.modtool.tickets", true)) {
            Emulator.getGameEnvironment().getModToolManager().quickTicket(client.getHabbo(), "Scripter", reason);
        }
    }
}
