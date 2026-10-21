package com.eu.habbo.habbohotel.habboway;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Pure rules of the two official quizzes of AIR's HabboWayQuizController: the Habbo Way quiz
 * ({@code HabboWay1}, opened from the Habbo Way window) and the safety quiz ({@code SafetyQuiz1},
 * opened from the safety booklet). The server hands out {@link #QUESTIONS_PER_QUIZ} of the
 * {@code quiz.<code>.question.<id>} questions, the client answers them in that order with the index
 * of the chosen {@code quiz.<code>.answer.<id>.<n>} text, and the server replies with the ids of
 * the wrongly answered questions.
 *
 * <p>The static methods without a quiz code keep answering for the Habbo Way quiz, as before the
 * safety quiz was added.
 */
public final class HabboWayQuiz {

    public static final String QUIZ_CODE = "HabboWay1";
    public static final String SAFETY_QUIZ_CODE = "SafetyQuiz1";
    public static final int QUESTIONS_PER_QUIZ = 5;

    /** Correct answer index per question id 0..9 of the official Habbo Way texts. */
    private static final int[] ANSWER_KEY = {2, 1, 2, 0, 1, 3, 1, 3, 0, 0};

    /**
     * Correct answer index per question id 0..12 of the official safety quiz texts: the one answer
     * of the three that keeps the personal details private or brings in a moderator.
     */
    private static final int[] SAFETY_ANSWER_KEY = {1, 1, 1, 1, 1, 0, 1, 1, 2, 2, 0, 0, 0};

    private HabboWayQuiz() {}

    public static boolean isKnownQuiz(String quizCode) {
        return answerKey(quizCode) != null;
    }

    /** The answer key of a quiz, or null for an unknown code. */
    private static int[] answerKey(String quizCode) {
        if (QUIZ_CODE.equals(quizCode)) return ANSWER_KEY;
        if (SAFETY_QUIZ_CODE.equals(quizCode)) return SAFETY_ANSWER_KEY;
        return null;
    }

    public static int questionCount() {
        return questionCount(QUIZ_CODE);
    }

    public static int questionCount(String quizCode) {
        int[] key = answerKey(quizCode);
        return key == null ? 0 : key.length;
    }

    public static int correctAnswer(int questionId) {
        return correctAnswer(QUIZ_CODE, questionId);
    }

    public static int correctAnswer(String quizCode, int questionId) {
        int[] key = answerKey(quizCode);
        if (key == null || questionId < 0 || questionId >= key.length) {
            return -1;
        }
        return key[questionId];
    }

    /** Draws {@link #QUESTIONS_PER_QUIZ} distinct Habbo Way question ids in random order. */
    public static int[] pickQuestions(Random random) {
        return pickQuestions(QUIZ_CODE, random);
    }

    /** Draws {@link #QUESTIONS_PER_QUIZ} distinct question ids of a quiz in random order. */
    public static int[] pickQuestions(String quizCode, Random random) {
        List<Integer> pool = new ArrayList<>();
        for (int id = 0; id < questionCount(quizCode); id++) {
            pool.add(id);
        }
        int[] picked = new int[Math.min(QUESTIONS_PER_QUIZ, pool.size())];
        for (int i = 0; i < picked.length; i++) {
            picked[i] = pool.remove(random.nextInt(pool.size()));
        }
        return picked;
    }

    public static List<Integer> grade(int[] questionIds, int[] answerIds) {
        return grade(QUIZ_CODE, questionIds, answerIds);
    }

    /**
     * Grades the answers given in question order; a missing (-1) or out of range answer counts as
     * wrong. Returns the wrongly answered question ids in question order.
     */
    public static List<Integer> grade(String quizCode, int[] questionIds, int[] answerIds) {
        List<Integer> wrong = new ArrayList<>();
        for (int i = 0; i < questionIds.length; i++) {
            int answer = i < answerIds.length ? answerIds[i] : -1;
            if (correctAnswer(quizCode, questionIds[i]) != answer) {
                wrong.add(questionIds[i]);
            }
        }
        return wrong;
    }

    public static boolean isPerfect(int[] questionIds, int[] answerIds) {
        return questionIds.length > 0 && grade(questionIds, answerIds).isEmpty();
    }

    /**
     * Habbo Way attempts are locked for two hours ("habbo.way.quiz.wait.indication"); the safety
     * booklet promises the safety quiz can be retaken at once ("safety.booklet.explanation.2").
     */
    public static long retryLockoutMs(String quizCode) {
        return SAFETY_QUIZ_CODE.equals(quizCode) ? 0L : HabboWayQuizSessions.RETRY_LOCKOUT_MS;
    }

    /** The achievement a perfect score progresses. */
    public static String graduateAchievement(String quizCode) {
        return SAFETY_QUIZ_CODE.equals(quizCode) ? "SafetyQuizGraduate" : "HabboWayGraduate";
    }

    static boolean isValidQuestionSet(int[] questionIds) {
        return isValidQuestionSet(QUIZ_CODE, questionIds);
    }

    static boolean isValidQuestionSet(String quizCode, int[] questionIds) {
        if (questionIds == null || questionIds.length != QUESTIONS_PER_QUIZ) {
            return false;
        }
        int count = questionCount(quizCode);
        int[] sorted = questionIds.clone();
        Arrays.sort(sorted);
        for (int i = 0; i < sorted.length; i++) {
            if (sorted[i] < 0 || sorted[i] >= count || (i > 0 && sorted[i] == sorted[i - 1])) {
                return false;
            }
        }
        return true;
    }
}
