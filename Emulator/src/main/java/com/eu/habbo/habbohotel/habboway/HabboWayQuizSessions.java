package com.eu.habbo.habbohotel.habboway;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * In-memory bookkeeping of the quizzes: the question set handed to each user per quiz (so
 * PostQuizAnswers is graded against what was actually asked) and the retry lockout that follows a
 * submitted attempt. The methods without a quiz code work on the Habbo Way quiz.
 */
public final class HabboWayQuizSessions {

    /** One attempt, then the quiz is locked for two hours. */
    public static final long RETRY_LOCKOUT_MS = 2L * 60L * 60L * 1000L;

    private static final HabboWayQuizSessions INSTANCE =
            new HabboWayQuizSessions(System::currentTimeMillis, new Random());

    private final Map<String, int[]> pendingQuestions = new ConcurrentHashMap<>();
    private final Map<String, Long> lockedUntil = new ConcurrentHashMap<>();
    private final LongSupplier clock;
    private final Random random;

    HabboWayQuizSessions(LongSupplier clock, Random random) {
        this.clock = clock;
        this.random = random;
    }

    public static HabboWayQuizSessions getInstance() {
        return INSTANCE;
    }

    private static String key(int userId, String quizCode) {
        return userId + ":" + quizCode;
    }

    public boolean isLocked(int userId) {
        return this.isLocked(userId, HabboWayQuiz.QUIZ_CODE);
    }

    public boolean isLocked(int userId, String quizCode) {
        String key = key(userId, quizCode);
        Long until = this.lockedUntil.get(key);
        if (until == null) {
            return false;
        }
        if (until <= this.clock.getAsLong()) {
            this.lockedUntil.remove(key, until);
            return false;
        }
        return true;
    }

    public int[] start(int userId) {
        return this.start(userId, HabboWayQuiz.QUIZ_CODE);
    }

    /**
     * Starts an attempt: returns the question ids to send, or an empty array while the user is
     * still locked out from a previous attempt.
     */
    public int[] start(int userId, String quizCode) {
        if (this.isLocked(userId, quizCode)) {
            return new int[0];
        }
        int[] questions = HabboWayQuiz.pickQuestions(quizCode, this.random);
        this.pendingQuestions.put(key(userId, quizCode), questions);
        return questions;
    }

    public int[] pending(int userId) {
        return this.pending(userId, HabboWayQuiz.QUIZ_CODE);
    }

    /** The question set the user is currently answering, or null. */
    public int[] pending(int userId, String quizCode) {
        return this.pendingQuestions.get(key(userId, quizCode));
    }

    public void finish(int userId) {
        this.finish(userId, HabboWayQuiz.QUIZ_CODE);
    }

    /** Closes the attempt and starts the retry lockout of that quiz. */
    public void finish(int userId, String quizCode) {
        String key = key(userId, quizCode);
        this.pendingQuestions.remove(key);

        long lockout = HabboWayQuiz.retryLockoutMs(quizCode);

        if (lockout > 0) {
            this.lockedUntil.put(key, this.clock.getAsLong() + lockout);
        }
    }

    public void forget(int userId) {
        for (String quizCode : new String[] {HabboWayQuiz.QUIZ_CODE, HabboWayQuiz.SAFETY_QUIZ_CODE}) {
            String key = key(userId, quizCode);
            this.pendingQuestions.remove(key);
            this.lockedUntil.remove(key);
        }
    }
}
