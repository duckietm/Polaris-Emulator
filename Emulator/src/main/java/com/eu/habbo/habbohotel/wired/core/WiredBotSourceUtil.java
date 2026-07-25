package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class WiredBotSourceUtil {
    public static final int SOURCE_BOT_NAME = 100;

    private WiredBotSourceUtil() {}

    public static int normalizeBotSource(int value) {
        return normalizeBotSource(value, SOURCE_BOT_NAME);
    }

    public static int normalizeBotSource(int value, int fallback) {
        switch (value) {
            case WiredSourceUtil.SOURCE_TRIGGER:
            case SOURCE_BOT_NAME:
            case WiredSourceUtil.SOURCE_SELECTOR:
            case WiredSourceUtil.SOURCE_SIGNAL:
                return value;
            default:
                return fallback;
        }
    }

    public static List<Bot> resolveBots(WiredContext ctx, Room room, int botSource, String botName) {
        if (ctx == null || room == null) {
            return Collections.emptyList();
        }

        if (botSource == SOURCE_BOT_NAME) {
            if (botName == null || botName.isEmpty()) {
                return Collections.emptyList();
            }

            List<Bot> bots = room.getBots(botName);
            return (bots != null) ? new ArrayList<>(bots) : Collections.emptyList();
        }

        List<Bot> resolved = new ArrayList<>();

        for (RoomUnit roomUnit : WiredSourceUtil.resolveUsers(ctx, botSource)) {
            Bot bot = room.getBot(roomUnit);

            if (bot != null && !resolved.contains(bot)) {
                resolved.add(bot);
            }
        }

        return resolved;
    }

    public static boolean requiresTriggeringUser(int botSource) {
        return botSource == WiredSourceUtil.SOURCE_TRIGGER;
    }
}
