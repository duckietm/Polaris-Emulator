package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomTrade;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.generic.alerts.GenericAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.HotelWillCloseInMinutesComposer;
import com.eu.habbo.threading.runnables.ShutdownEmulator;

public class ShutdownCommand extends Command {
    public ShutdownCommand() {
        super("cmd_shutdown", Emulator.getTexts().getValue("commands.keys.cmd_shutdown").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        var reason = new StringBuilder("-");
        int minutes = 0;
        if (params.length > 2) {
            reason = new StringBuilder();
            for (int i = 1; i < params.length; i++) {
                reason.append(params[i]).append(" ");
            }
        } else {
            if (params.length == 2) {
                try {
                    minutes = Integer.parseInt(params[1]);
                } catch (Exception e) {
                    reason = new StringBuilder(params[1]);
                }
            }
        }

        ServerMessage message;
        if (!reason.toString().equals("-")) {
            message = new GenericAlertComposer("<b>" + Emulator.getTexts().getValue("generic.warning") + "</b> \r\n" +
                    Emulator.getTexts().getValue("generic.shutdown").replace("%minutes%", minutes + "") + "\r\n" +
                    Emulator.getTexts().getValue("generic.reason.specified") + ": <b>" + reason + "</b>\r" +
                    "\r" +
                    "- " + gameClient.getHabbo().getHabboInfo().getUsername()).compose();
        } else {
            message = new HotelWillCloseInMinutesComposer(minutes).compose();
        }
        RoomTrade.TRADING_ENABLED = false;
        ShutdownEmulator.timestamp = Emulator.getIntUnixTimestamp() + (60 * minutes);
        Emulator.getThreading().run(new ShutdownEmulator(message), (long) minutes * 60 * 1000);
        return true;
    }
}
