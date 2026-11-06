package polaris.messages.incoming.hotelway;

import polaris.hotel.hotelway.HotelWayQuiz;
import polaris.hotel.hotelway.HotelWayQuizSessions;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.hotelway.QuizDataComposer;

/**
 * GetQuizQuestions (1296): the client opens the Habbo Way or the safety
 * quiz and asks for its question ids; answered with QuizData (2927). A user still inside the
 * retry lockout receives an empty question list.
 */
public class GetQuizQuestionsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        String quizCode = this.packet.readString();

        if (!HotelWayQuiz.isKnownQuiz(quizCode)) {
            return;
        }

        int[] questionIds = HotelWayQuizSessions.getInstance()
                .start(this.client.getUser().getUserInfo().getId(), quizCode);

        this.client.sendResponse(new QuizDataComposer(quizCode, questionIds));
    }
}
