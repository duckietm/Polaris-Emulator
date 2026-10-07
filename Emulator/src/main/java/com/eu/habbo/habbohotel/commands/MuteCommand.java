package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserIgnoredComposer;

public class MuteCommand extends Command {
    // ":mute <user>" without a duration is a long mute; longer durations are capped.
    static final int DEFAULT_SECONDS = 365 * 24 * 60 * 60;
    static final int MAX_SECONDS = 10 * DEFAULT_SECONDS;

    public MuteCommand() {
        super("cmd_mute", Emulator.getTexts().getValue("commands.keys.cmd_mute").split(";"));
    }

    /**
     * Resolves the optional duration argument in seconds, or -1 when it is not a positive number.
     */
    static int resolveDurationSeconds(String[] params) {
        if (params.length < 3) {
            return DEFAULT_SECONDS;
        }

        String raw = params[2].trim();

        if (!raw.matches("\\d{1,18}")) {
            return -1;
        }

        long seconds = Long.parseLong(raw);

        if (seconds <= 0) {
            return -1;
        }

        return (int) Math.min(seconds, MAX_SECONDS);
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length == 1) {
            gameClient
                    .getHabbo()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_mute.not_specified"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(params[1]);

        if (habbo == null) {
            gameClient
                    .getHabbo()
                    .whisper(
                            Emulator.getTexts()
                                    .getValue("commands.error.cmd_mute.not_found")
                                    .replace("%user%", params[1]),
                            RoomChatMessageBubbles.ALERT);
            return true;
        } else {
            if (habbo == gameClient.getHabbo()) {
                gameClient
                        .getHabbo()
                        .whisper(
                                Emulator.getTexts().getValue("commands.error.cmd_mute.self"),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }

            if (!CommandTargetGuard.canTarget(gameClient.getHabbo(), habbo)) {
                gameClient
                        .getHabbo()
                        .whisper(
                                Emulator.getTexts().getValue("commands.error.cmd_ban.target_rank_higher"),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }

            int duration = resolveDurationSeconds(params);

            if (duration <= 0) {
                gameClient
                        .getHabbo()
                        .whisper(
                                Emulator.getTexts().getValue("commands.error.cmd_mute.time"),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }

            habbo.mute(duration, false);

            if (habbo.getHabboInfo().getCurrentRoom() != null) {
                habbo.getHabboInfo()
                        .getCurrentRoom()
                        .sendComposer(new RoomUserIgnoredComposer(habbo, RoomUserIgnoredComposer.MUTED)
                                .compose()); // : RoomUserIgnoredComposer.UNIGNORED
            }

            gameClient
                    .getHabbo()
                    .whisper(
                            Emulator.getTexts()
                                    .getValue("commands.succes.cmd_mute.muted")
                                    .replace("%user%", params[1]),
                            RoomChatMessageBubbles.ALERT);
        }

        return true;
    }
}
