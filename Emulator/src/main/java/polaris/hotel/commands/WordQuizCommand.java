package polaris.hotel.commands;

import java.util.Arrays;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;

public class WordQuizCommand extends Command {
    public WordQuizCommand() {
        super(
                "cmd_word_quiz",
                Emulator.getTexts().getValue("commands.keys.cmd_word_quiz").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (!gameClient.getUser().getUserInfo().getCurrentRoom().hasActiveWordQuiz()) {
            if (params.length == 1) {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts().getValue("commands.description.cmd_word_quiz"),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }
            StringBuilder question = new StringBuilder();
            int duration = 60;

            try {
                duration = Integer.parseInt(params[params.length - 1]);
                params = Arrays.copyOf(params, params.length - 1);
            } catch (Exception e) {
            }

            for (int i = 1; i < params.length; i++) {
                question.append(" ").append(params[i]);
            }

            gameClient.getUser().getUserInfo().getCurrentRoom().startWordQuiz(question.toString(), duration * 1000);
        }
        return true;
    }
}
