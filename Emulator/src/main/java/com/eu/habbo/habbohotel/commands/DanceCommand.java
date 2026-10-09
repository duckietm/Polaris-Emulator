package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.rooms.RoomUserAction;
import com.eu.habbo.habbohotel.users.DanceType;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserActionComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserDanceComposer;

public class DanceCommand extends Command {
    public DanceCommand() {
        super(
            "cmd_dance",
            Emulator.getTexts().getValue("commands.keys.cmd_dance").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        Habbo habbo = gameClient.getHabbo();
        if (habbo == null || habbo.getHabboInfo().getCurrentRoom() == null) {
            return true;
        }

        if (params.length == 2) {
            int danceId;

            try {
                danceId = Integer.parseInt(params[1]);
            } catch (NumberFormatException e) {
                habbo.whisper(
                    Emulator.getTexts().getValue("commands.error.cmd_dance.invalid_dance"),
                    RoomChatMessageBubbles.ALERT);
                return true;
            }

            if (danceId == RoomUserAction.SIX_SEVEN.getAction()) {
                sixSeven(habbo);
                return true;
            }

            if (danceId < 0 || danceId > 4) {
                habbo.whisper(
                    Emulator.getTexts().getValue("commands.error.cmd_dance.outside_bounds"),
                    RoomChatMessageBubbles.ALERT);
                return true;
            }

            habbo.getRoomUnit().setDanceType(DanceType.values()[danceId]);
            habbo.getHabboInfo()
                .getCurrentRoom()
                .sendComposer(new RoomUserDanceComposer(habbo.getRoomUnit()).compose());

            String danceName = danceId == 0 ? "stop" : "dance " + danceId;
            habbo.whisper("You are now doing " + danceName + "!", RoomChatMessageBubbles.NORMAL);
        } else {
            habbo.whisper(Emulator.getTexts().getValue("commands.error.cmd_dance.usage"), RoomChatMessageBubbles.ALERT);
        }

        return true;
    }

    // :dance 67 plays the one-shot six-seven move; a running dance would hide it.
    private static void sixSeven(Habbo habbo) {
        Room room = habbo.getHabboInfo().getCurrentRoom();

        if (habbo.getRoomUnit().getDanceType() != DanceType.NONE) {
            habbo.getRoomUnit().setDanceType(DanceType.NONE);
            room.sendComposer(new RoomUserDanceComposer(habbo.getRoomUnit()).compose());
        }

        room.sendComposer(new RoomUserActionComposer(habbo.getRoomUnit(), RoomUserAction.SIX_SEVEN).compose());
    }
}
