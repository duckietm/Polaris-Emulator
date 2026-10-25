package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.rooms.ForwardToRoomComposer;

public class SummonCommand extends Command {
    public SummonCommand() {
        super(
                "cmd_summon",
                Emulator.getTexts().getValue("commands.keys.cmd_summon").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (gameClient.getHabbo().getHabboInfo().getCurrentRoom() == null) return true;

        if (params.length >= 2) {
            Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(params[1]);

            if (habbo == null) {
                gameClient
                        .getHabbo()
                        .whisper(
                                Emulator.getTexts()
                                        .getValue("commands.error.cmd_summon.not_found")
                                        .replace("%user%", params[1]),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }

            if (gameClient
                    .getHabbo()
                    .getHabboInfo()
                    .getUsername()
                    .equals(habbo.getHabboInfo().getUsername())) {
                gameClient
                        .getHabbo()
                        .whisper(
                                Emulator.getTexts()
                                        .getValue("commands.generic.cmd_summon.self")
                                        .replace("%user%", params[1]),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }

            if (CommandTargetGuard.refuse(gameClient.getHabbo(), habbo)) {
                return true;
            }

            if (gameClient.getHabbo().getHabboInfo().getCurrentRoom()
                    == habbo.getHabboInfo().getCurrentRoom()) {
                gameClient
                        .getHabbo()
                        .whisper(
                                Emulator.getTexts()
                                        .getValue("commands.generic.cmd_summon.same_room")
                                        .replace("%user%", params[1]),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }

            Room room = habbo.getHabboInfo().getCurrentRoom();
            if (room != null) {
                Emulator.getGameEnvironment().getRoomManager().logExit(habbo);

                room.removeHabbo(habbo, true);

                habbo.getHabboInfo().setCurrentRoom(null);
            }

            Emulator.getGameEnvironment()
                    .getRoomManager()
                    .enterRoom(
                            habbo,
                            gameClient
                                    .getHabbo()
                                    .getHabboInfo()
                                    .getCurrentRoom()
                                    .getId(),
                            "",
                            true);

            habbo.getClient()
                    .sendResponse(new ForwardToRoomComposer(gameClient
                            .getHabbo()
                            .getHabboInfo()
                            .getCurrentRoom()
                            .getId()));

            gameClient
                    .getHabbo()
                    .whisper(
                            Emulator.getTexts()
                                    .getValue("commands.succes.cmd_summon.summoned")
                                    .replace("%user%", params[1]),
                            RoomChatMessageBubbles.ALERT);

            habbo.alert(Emulator.getTexts()
                    .getValue("commands.generic.cmd_summon.been_summoned")
                    .replace("%user%", gameClient.getHabbo().getHabboInfo().getUsername()));

            return true;
        } else {
            gameClient
                    .getHabbo()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_summon.forgot_username"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }
    }
}
