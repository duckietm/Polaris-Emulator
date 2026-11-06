package polaris.messages.incoming.hotelway;

import java.util.List;
import polaris.Emulator;
import polaris.hotel.achievements.Achievement;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.hotelway.HotelWayQuiz;
import polaris.hotel.hotelway.HotelWayQuizSessions;
import polaris.hotel.users.User;
import polaris.hotel.users.UserBadge;
import polaris.hotel.users.inventory.BadgesComponent;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.hotelway.QuizResultsComposer;
import polaris.messages.outgoing.inventory.AddUserItemComposer;
import polaris.messages.outgoing.inventory.InventoryRefreshComposer;

/**
 * PostQuizAnswers (3720): the chosen answer index per question, in the order
 * of the QuizData question ids; answered with QuizResults (2772) carrying the
 * wrongly answered question ids. A perfect score earns the official
 * "Habbo Way Graduate" badge, or "Safety Quiz Graduate" for the safety quiz.
 */
public class PostQuizAnswersEvent extends MessageHandler {

    /** Achievement of the official hotel; the badge is given directly when it is not seeded. */
    public static final String ACHIEVEMENT_NAME = "HabboWayGraduate";

    public static final String BADGE_CODE = "ACH_HabboWayGraduate1";
    private static final int MAX_ANSWERS = 64;

    @Override
    public void handle() throws Exception {
        String quizCode = this.packet.readString();
        int count = this.packet.readInt();

        if (!HotelWayQuiz.isKnownQuiz(quizCode) || count < 0 || count > MAX_ANSWERS) {
            return;
        }

        int[] answerIds = new int[count];
        for (int i = 0; i < count; i++) {
            answerIds[i] = this.packet.readInt();
        }

        User user = this.client.getUser();
        HotelWayQuizSessions sessions = HotelWayQuizSessions.getInstance();
        int[] questionIds = sessions.pending(user.getUserInfo().getId(), quizCode);

        if (questionIds == null) {
            return;
        }

        List<Integer> wrongQuestionIds = HotelWayQuiz.grade(quizCode, questionIds, answerIds);
        sessions.finish(user.getUserInfo().getId(), quizCode);

        if (wrongQuestionIds.isEmpty()) {
            awardGraduateBadge(user, HotelWayQuiz.graduateAchievement(quizCode));
        }

        this.client.sendResponse(new QuizResultsComposer(quizCode, wrongQuestionIds));
    }

    private static void awardGraduateBadge(User user, String achievementName) {
        Achievement achievement =
                Emulator.getGameEnvironment().getAchievementManager().getAchievement(achievementName);

        if (achievement != null) {
            AchievementManager.progressAchievement(user, achievement);
            return;
        }

        String badgeCode = "ACH_" + achievementName + "1";

        if (user.getInventory().getBadgesComponent().hasBadge(badgeCode)) {
            return;
        }

        UserBadge badge = BadgesComponent.createBadge(badgeCode, user);
        user.getClient()
                .sendResponse(new AddUserItemComposer(badge.getId(), AddUserItemComposer.AddUserItemCategory.BADGE));
        user.getClient().sendResponse(new InventoryRefreshComposer());
    }
}
