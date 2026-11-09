package polaris.hotel.commands;

import java.util.Arrays;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomChatMessageBubbles;

public class WordQuizCommand extends Command {
    static final int DEFAULT_DURATION_SECONDS = 60;
    static final int MIN_DURATION_SECONDS = 5;
    static final int MAX_DURATION_SECONDS = 3600;

    public WordQuizCommand() {
        super(
                "cmd_word_quiz",
                Emulator.getTexts().getValue("commands.keys.cmd_word_quiz").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        Room room = gameClient.getUser().getUserInfo().getCurrentRoom();
        if (room == null) return true;

        if (!room.hasActiveWordQuiz()) {
            if (params.length == 1) {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts().getValue("commands.description.cmd_word_quiz"),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }
            StringBuilder question = new StringBuilder();
            int duration = DEFAULT_DURATION_SECONDS;

            try {
                duration = Integer.parseInt(params[params.length - 1]);
                params = Arrays.copyOf(params, params.length - 1);
            } catch (Exception e) {
            }

            for (int i = 1; i < params.length; i++) {
                question.append(" ").append(params[i]);
            }

            room.startWordQuiz(question.toString(), clampDurationSeconds(duration) * 1000);
        }
        return true;
    }

    /** 5 seconds to an hour: a larger value overflowed the milliseconds, zero or less ended the quiz at once. */
    static int clampDurationSeconds(int seconds) {
        return Math.max(MIN_DURATION_SECONDS, Math.min(MAX_DURATION_SECONDS, seconds));
    }
}
