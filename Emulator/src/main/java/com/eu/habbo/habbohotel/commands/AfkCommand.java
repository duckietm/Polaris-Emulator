package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;

public class AfkCommand extends Command {

    private static final String DEFAULT_KEYS = "afk;absent;away";
    private static final String DEFAULT_AWAY =
            "You are now away. When you send a message you will automaticly will be back.";
    private static final String DEFAULT_BACK = "Welcome back!";

    public AfkCommand() {
        super(
                null,
                Emulator.getTexts()
                        .getValueQuietly("commands.keys.cmd_afk", DEFAULT_KEYS)
                        .split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        Habbo habbo = gameClient.getHabbo();

        if (habbo == null || habbo.getRoomUnit() == null) {
            return true;
        }

        Room room = habbo.getHabboInfo().getCurrentRoom();

        if (room == null) {
            return true;
        }

        if (habbo.getRoomUnit().isIdle()) {
            room.unIdle(habbo);
            habbo.whisper(
                    Emulator.getTexts().getValueQuietly("commands.generic.cmd_afk.back", DEFAULT_BACK),
                    RoomChatMessageBubbles.NORMAL);
        } else {
            room.idle(habbo);
            habbo.whisper(
                    Emulator.getTexts().getValueQuietly("commands.generic.cmd_afk.away", DEFAULT_AWAY),
                    RoomChatMessageBubbles.NORMAL);
        }

        return true;
    }
}
