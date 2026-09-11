package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.MaintenanceMode;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.messages.outgoing.generic.alerts.HotelClosesAndWillOpenAtComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.HotelWillCloseInMinutesAndBackInComposer;

public class MaintenanceCommand extends Command {
    public MaintenanceCommand() {
        super(
                "cmd_maintenance",
                Emulator.getTexts()
                        .getValue("commands.keys.cmd_maintenance", "maintenance;maintenancemode")
                        .split(";"));
    }

    private static int parseOr(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static int parsePositive(String[] params, int index, int fallback) {
        int value = params.length > index ? parseOr(params[index], fallback) : fallback;

        return Math.max(1, value);
    }

    private static String clockOf() {
        return String.format("%02d:%02d", MaintenanceMode.getReopenHour(), MaintenanceMode.getReopenMinute());
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length < 2) {
            String state = MaintenanceMode.isEnabled()
                    ? Emulator.getTexts().getValue("commands.generic.cmd_maintenance.on", "ON")
                    : Emulator.getTexts().getValue("commands.generic.cmd_maintenance.off", "OFF");

            gameClient
                    .getHabbo()
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

        // "warn 10 30": the hotel closes in ten minutes and is back in thirty. It is the only way
        // players hear about it before the door shuts.
        if (action.equals("warn")) {
            int closeInMinutes = parsePositive(params, 2, 5);
            int reopenInMinutes = parsePositive(params, 3, 30);

            Emulator.getGameServer()
                    .getGameClientManager()
                    .sendBroadcastResponse(
                            new HotelWillCloseInMinutesAndBackInComposer(closeInMinutes, reopenInMinutes).compose());

            gameClient
                    .getHabbo()
                    .whisper(
                            Emulator.getTexts()
                                    .getValue(
                                            "commands.succes.cmd_maintenance.warn",
                                            "Told everyone the hotel closes in %close% minutes, back in %reopen%.")
                                    .replace("%close%", Integer.toString(closeInMinutes))
                                    .replace("%reopen%", Integer.toString(reopenInMinutes)),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        // "at 14:30": when the hotel says it will be back, which the closed notices carry.
        if (action.equals("at")) {
            String[] clock = params.length > 2 ? params[2].split(":") : new String[0];
            int hour = clock.length > 0 ? parseOr(clock[0], -1) : -1;
            int minute = clock.length > 1 ? parseOr(clock[1], 0) : 0;

            MaintenanceMode.setReopenTime(hour, minute);

            gameClient
                    .getHabbo()
                    .whisper(
                            MaintenanceMode.hasReopenTime()
                                    ? Emulator.getTexts()
                                            .getValue("commands.succes.cmd_maintenance.at", "Back at %time%.")
                                            .replace("%time%", clockOf())
                                    : Emulator.getTexts()
                                            .getValue(
                                                    "commands.succes.cmd_maintenance.at.cleared",
                                                    "The hotel no longer says when it will be back."),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        boolean enable;

        if (action.equals("on") || action.equals("enable") || action.equals("1")) {
            enable = true;
        } else if (action.equals("off") || action.equals("disable") || action.equals("0")) {
            enable = false;
        } else {
            gameClient
                    .getHabbo()
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

        // Whoever is already inside keeps playing - maintenance only shuts the door - but they are
        // told it closed and when it is back, which is the whole point of the notice.
        if (enable && MaintenanceMode.hasReopenTime()) {
            Emulator.getGameServer()
                    .getGameClientManager()
                    .sendBroadcastResponse(new HotelClosesAndWillOpenAtComposer(
                                    MaintenanceMode.getReopenHour(), MaintenanceMode.getReopenMinute(), false)
                            .compose());
        }

        String key = enable ? "commands.succes.cmd_maintenance.on" : "commands.succes.cmd_maintenance.off";
        String fallback = enable
                ? "Maintenance mode enabled. Only rank %rank%+ can log in now."
                : "Maintenance mode disabled. Everyone can log in again.";

        gameClient
                .getHabbo()
                .whisper(
                        Emulator.getTexts()
                                .getValue(key, fallback)
                                .replace("%rank%", Integer.toString(MaintenanceMode.getMinRank())),
                        RoomChatMessageBubbles.ALERT);
        return true;
    }
}
