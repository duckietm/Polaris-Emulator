package com.eu.habbo.messages.incoming.habboway;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.Achievement;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.habboway.HabboWayQuiz;
import com.eu.habbo.habbohotel.habboway.HabboWayQuizSessions;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboBadge;
import com.eu.habbo.habbohotel.users.inventory.BadgesComponent;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.unknown.UnknownHabboWayQuizComposer;
import java.util.List;

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

        if (!HabboWayQuiz.isKnownQuiz(quizCode) || count < 0 || count > MAX_ANSWERS) {
            return;
        }

        int[] answerIds = new int[count];
        for (int i = 0; i < count; i++) {
            answerIds[i] = this.packet.readInt();
        }

        Habbo habbo = this.client.getHabbo();
        HabboWayQuizSessions sessions = HabboWayQuizSessions.getInstance();
        int[] questionIds = sessions.pending(habbo.getHabboInfo().getId(), quizCode);

        if (questionIds == null) {
            return;
        }

        List<Integer> wrongQuestionIds = HabboWayQuiz.grade(quizCode, questionIds, answerIds);
        sessions.finish(habbo.getHabboInfo().getId(), quizCode);

        if (wrongQuestionIds.isEmpty()) {
            awardGraduateBadge(habbo, HabboWayQuiz.graduateAchievement(quizCode));
        }

        this.client.sendResponse(new UnknownHabboWayQuizComposer(quizCode, wrongQuestionIds));
    }

    private static void awardGraduateBadge(Habbo habbo, String achievementName) {
        Achievement achievement =
                Emulator.getGameEnvironment().getAchievementManager().getAchievement(achievementName);

        if (achievement != null) {
            AchievementManager.progressAchievement(habbo, achievement);
            return;
        }

        String badgeCode = "ACH_" + achievementName + "1";

        if (habbo.getInventory().getBadgesComponent().hasBadge(badgeCode)) {
            return;
        }

        HabboBadge badge = BadgesComponent.createBadge(badgeCode, habbo);
        habbo.getClient()
                .sendResponse(new AddHabboItemComposer(badge.getId(), AddHabboItemComposer.AddHabboItemCategory.BADGE));
        habbo.getClient().sendResponse(new InventoryRefreshComposer());
    }
}
