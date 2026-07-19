package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.CatalogManager;
import com.eu.habbo.habbohotel.gameclients.GameClient;

import java.util.concurrent.TimeUnit;


public class AboutCommand extends Command {
    public AboutCommand() {
        super(null, new String[]{"about", "info", "online", "server"});
    }

    public static final String NITRO_INFO_SENTINEL = "[NITRO_INFO_V1]";
    public static final String REPORT_ISSUES_URL = "https://github.com/duckietm/Nitro-V3/issues";

    @Override
    public boolean handle(GameClient gameClient, String[] params) {

        Emulator.getRuntime().gc();

        int seconds = Emulator.getOnlineTime();
        int day = (int) TimeUnit.SECONDS.toDays(seconds);
        long hours = TimeUnit.SECONDS.toHours(seconds) - (day * 24L);
        long minute = TimeUnit.SECONDS.toMinutes(seconds) - (TimeUnit.SECONDS.toHours(seconds) * 60);
        long second = TimeUnit.SECONDS.toSeconds(seconds) - (TimeUnit.SECONDS.toMinutes(seconds) * 60);

        StringBuilder message = new StringBuilder();
        message.append(NITRO_INFO_SENTINEL).append("\r");
        message.append("<b>").append(Emulator.version).append("</b>\r\n");

        if (Emulator.getConfig().getBoolean("info.shown", true)) {
            message.append("<b>Hotel Statistics</b>\r")
                    .append("- Online Users: ").append(Emulator.getGameEnvironment().getHabboManager().getOnlineCount()).append("\r")
                    .append("- Active Rooms: ").append(Emulator.getGameEnvironment().getRoomManager().getActiveRooms().size()).append("\r")
                    .append("- Shop: ").append(Emulator.getGameEnvironment().getCatalogManager().catalogPages.size()).append(" pages and ").append(CatalogManager.catalogItemAmount).append(" items.\r")
                    .append("- Furni: ").append(Emulator.getGameEnvironment().getItemManager().getItems().size()).append(" item definitions\r")
                    .append("\n")
                    .append("<b>Server Statistics</b>\r")
                    .append("- Uptime: ").append(day).append(day == 1 ? " day, " : " days, ").append(hours).append(hours == 1 ? " hour, " : " hours, ").append(minute).append(minute == 1 ? " minute, " : " minutes, ").append(second).append(second == 1 ? " second!" : " seconds!").append("\r")
                    .append("- RAM Usage: ").append((Emulator.getRuntime().totalMemory() - Emulator.getRuntime().freeMemory()) / (1024 * 1024)).append("/").append((Emulator.getRuntime().freeMemory()) / (1024 * 1024)).append("MB\r")
                    .append("- CPU Cores: ").append(Emulator.getRuntime().availableProcessors()).append("\r")
                    .append("- Total Memory: ").append(Emulator.getRuntime().maxMemory() / (1024 * 1024)).append("MB\r\n");
        }

        message.append("<b>Credits</b>\r")
                .append("- The General\r")
                .append("- Krews Team\r")
                .append("- DuckieTM, simoleo89, Medievalshell, Lorenzo (the wired master), Remco\r\n")
                .append("Report issues at: ").append(REPORT_ISSUES_URL);

        gameClient.getHabbo().alert(message.toString());
        return true;
    }
}
