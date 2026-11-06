package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.MaintenanceMode;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;

public class MaintenanceCommand extends Command {
    public MaintenanceCommand() {
        super(
                "cmd_maintenance",
                Emulator.getTexts()
                        .getValue("commands.keys.cmd_maintenance", "maintenance;maintenancemode")
                        .split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length < 2) {
            String state = MaintenanceMode.isEnabled()
                    ? Emulator.getTexts().getValue("commands.generic.cmd_maintenance.on", "ON")
                    : Emulator.getTexts().getValue("commands.generic.cmd_maintenance.off", "OFF");

            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts()
                                    .getValue(
                                            "commands.generic.cmd_maintenance.status",
                                            "Maintenance mode is %state% (min rank %rank%): %message%")
                                    .replace("%state%", state)
                                    .replace("%rank%", Integer.toString(MaintenanceMode.getMinRank()))
                                    .replace("%message%", MaintenanceMode.getMessage()),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        String action = params[1].toLowerCase();
        boolean enable;

        if (action.equals("on") || action.equals("enable") || action.equals("1")) {
            enable = true;
        } else if (action.equals("off") || action.equals("disable") || action.equals("0")) {
            enable = false;
        } else {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts()
                                    .getValue(
                                            "commands.error.cmd_maintenance", "Usage: :maintenance <on|off> [message]"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        String message = null;

        if (enable && params.length > 2) {
            StringBuilder builder = new StringBuilder();
            for (int i = 2; i < params.length; i++) builder.append(params[i]).append(' ');
            message = builder.toString().trim();
        }

        MaintenanceMode.setEnabled(enable, message);

        String key = enable ? "commands.succes.cmd_maintenance.on" : "commands.succes.cmd_maintenance.off";
        String fallback = enable
                ? "Maintenance mode enabled. Only rank %rank%+ can log in now."
                : "Maintenance mode disabled. Everyone can log in again.";

        gameClient
                .getUser()
                .whisper(
                        Emulator.getTexts()
                                .getValue(key, fallback)
                                .replace("%rank%", Integer.toString(MaintenanceMode.getMinRank())),
                        RoomChatMessageBubbles.ALERT);
        return true;
    }
}
