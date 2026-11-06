package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;

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
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_mute.not_specified"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        User user = Emulator.getGameEnvironment().getUserManager().getUser(params[1]);

        if (user == null) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts()
                                    .getValue("commands.error.cmd_mute.not_found")
                                    .replace("%user%", params[1]),
                            RoomChatMessageBubbles.ALERT);
            return true;
        } else {
            if (user == gameClient.getUser()) {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts().getValue("commands.error.cmd_mute.self"),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }

            if (!CommandTargetGuard.canTarget(gameClient.getUser(), user)) {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts().getValue("commands.error.cmd_ban.target_rank_higher"),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }

            int duration = resolveDurationSeconds(params);

            if (duration <= 0) {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts().getValue("commands.error.cmd_mute.time"),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }

            // User.mute tells the muted user (RemainingMutePeriod); the room is not told.
            user.mute(duration, false);

            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts()
                                    .getValue("commands.succes.cmd_mute.muted")
                                    .replace("%user%", params[1]),
                            RoomChatMessageBubbles.ALERT);
        }

        return true;
    }
}
