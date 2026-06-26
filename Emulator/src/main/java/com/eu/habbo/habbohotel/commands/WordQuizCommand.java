package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;

import java.util.Arrays;

public class WordQuizCommand extends Command {
    public WordQuizCommand() {
        super("cmd_word_quiz", Emulator.getTexts().getValue("commands.keys.cmd_word_quiz").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (!gameClient.getHabbo().getHabboInfo().getCurrentRoom().hasActiveWordQuiz()) {
            if(params.length == 1) {
                gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.description.cmd_word_quiz"), RoomChatMessageBubbles.ALERT);
                return  true;
            }
            var question = new StringBuilder();
            int duration = 60;

            try {
                duration =  Integer.parseInt(params[params.length-1]);
                params = Arrays.copyOf(params, params.length-1);
            }
            catch (Exception e) {}

            for (int i = 1; i < params.length; i++) {
                question.append(" ").append(params[i]);
            }

            gameClient.getHabbo().getHabboInfo().getCurrentRoom().startWordQuiz(question.toString(), duration * 1000);
        }
        return true;
    }
}