package com.eu.habbo.messages.incoming.habboway;

import com.eu.habbo.habbohotel.habboway.HabboWayQuiz;
import com.eu.habbo.habbohotel.habboway.HabboWayQuizSessions;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.habboway.HabboWayQuizComposer2;

/**
 * GetQuizQuestions (1296): the client opens the Habbo Way or the safety
 * quiz and asks for its question ids; answered with QuizData (2927). A user still inside the
 * retry lockout receives an empty question list.
 */
public class GetQuizQuestionsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        String quizCode = this.packet.readString();

        if (!HabboWayQuiz.isKnownQuiz(quizCode)) {
            return;
        }

        int[] questionIds = HabboWayQuizSessions.getInstance()
                .start(this.client.getHabbo().getHabboInfo().getId(), quizCode);

        this.client.sendResponse(new HabboWayQuizComposer2(quizCode, questionIds));
    }
}
