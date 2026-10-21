package com.eu.habbo.habbohotel.habboway;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class HabboWayQuizTest {

    @Test
    void picksFiveDistinctQuestionsOutOfTen() {
        for (int seed = 0; seed < 50; seed++) {
            int[] picked = HabboWayQuiz.pickQuestions(new Random(seed));
            assertEquals(HabboWayQuiz.QUESTIONS_PER_QUIZ, picked.length);
            assertTrue(HabboWayQuiz.isValidQuestionSet(picked), "seed " + seed);
        }
        assertEquals(10, HabboWayQuiz.questionCount());
    }

    @Test
    void gradesAgainstTheClientAnswerKey() {
        int[] questions = {0, 1, 2, 3, 4};
        assertTrue(HabboWayQuiz.grade(questions, new int[] {2, 1, 2, 0, 1}).isEmpty());
        assertTrue(HabboWayQuiz.isPerfect(questions, new int[] {2, 1, 2, 0, 1}));

        List<Integer> wrong = HabboWayQuiz.grade(questions, new int[] {0, 1, 2, 3, 1});
        assertEquals(List.of(0, 3), wrong);

        int[] rest = {5, 6, 7, 8, 9};
        assertTrue(HabboWayQuiz.grade(rest, new int[] {3, 1, 3, 0, 0}).isEmpty());
    }

    @Test
    void missingOrForeignAnswersCountAsWrong() {
        int[] questions = {0, 1};
        assertEquals(List.of(0, 1), HabboWayQuiz.grade(questions, new int[0]));
        assertEquals(List.of(1), HabboWayQuiz.grade(questions, new int[] {2, -1}));
        assertEquals(List.of(1), HabboWayQuiz.grade(questions, new int[] {2, 99}));
        assertFalse(HabboWayQuiz.isPerfect(new int[0], new int[0]));
        assertEquals(-1, HabboWayQuiz.correctAnswer(10));
    }

    @Test
    void sessionsLockTheUserForTwoHoursAfterAnAttempt() {
        AtomicLong now = new AtomicLong(1_000L);
        HabboWayQuizSessions sessions = new HabboWayQuizSessions(now::get, new Random(1));

        int[] questions = sessions.start(7);
        assertEquals(HabboWayQuiz.QUESTIONS_PER_QUIZ, questions.length);
        assertArrayEquals(questions, sessions.pending(7));
        assertFalse(sessions.isLocked(7));

        sessions.finish(7);
        assertNull(sessions.pending(7));
        assertTrue(sessions.isLocked(7));
        assertEquals(0, sessions.start(7).length);
        assertNull(sessions.pending(7));

        now.addAndGet(HabboWayQuizSessions.RETRY_LOCKOUT_MS - 1);
        assertTrue(sessions.isLocked(7));

        now.addAndGet(1);
        assertFalse(sessions.isLocked(7));
        assertNotNull(sessions.pending(7) == null ? sessions.start(7) : null);
        assertEquals(HabboWayQuiz.QUESTIONS_PER_QUIZ, sessions.pending(7).length);

        sessions.forget(7);
        assertNull(sessions.pending(7));
    }

    @Test
    void lockoutIsPerUser() {
        HabboWayQuizSessions sessions = new HabboWayQuizSessions(() -> 5L, new Random(2));
        sessions.start(1);
        sessions.finish(1);
        assertTrue(sessions.isLocked(1));
        assertFalse(sessions.isLocked(2));
        assertEquals(HabboWayQuiz.QUESTIONS_PER_QUIZ, sessions.start(2).length);
    }

    @Test
    void safetyQuizHasItsOwnKeyAndNoLockout() {
        String code = HabboWayQuiz.SAFETY_QUIZ_CODE;
        assertTrue(HabboWayQuiz.isKnownQuiz(code));
        assertFalse(HabboWayQuiz.isKnownQuiz("Unknown1"));
        assertEquals(13, HabboWayQuiz.questionCount(code));
        assertEquals("SafetyQuizGraduate", HabboWayQuiz.graduateAchievement(code));
        assertEquals("HabboWayGraduate", HabboWayQuiz.graduateAchievement(HabboWayQuiz.QUIZ_CODE));

        for (int seed = 0; seed < 50; seed++) {
            assertTrue(HabboWayQuiz.isValidQuestionSet(code, HabboWayQuiz.pickQuestions(code, new Random(seed))));
        }

        assertTrue(HabboWayQuiz.grade(code, new int[] {0, 5, 8, 9, 12}, new int[] {1, 0, 2, 2, 0})
                .isEmpty());
        assertEquals(List.of(5), HabboWayQuiz.grade(code, new int[] {0, 5}, new int[] {1, 1}));

        HabboWayQuizSessions sessions = new HabboWayQuizSessions(() -> 5L, new Random(3));
        assertEquals(HabboWayQuiz.QUESTIONS_PER_QUIZ, sessions.start(9, code).length);
        assertNull(sessions.pending(9));
        sessions.finish(9, code);
        assertFalse(sessions.isLocked(9, code));
        assertEquals(HabboWayQuiz.QUESTIONS_PER_QUIZ, sessions.start(9, code).length);

        sessions.start(9);
        sessions.finish(9);
        assertTrue(sessions.isLocked(9));
        assertFalse(sessions.isLocked(9, code));
    }
}
