package com.eu.habbo.messages.incoming.polls;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PollAnswerInputGuardContractTest {
    private static String source() throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/polls/AnswerPollEvent.java"));
    }

    @Test
    void pollAnswerCountAndPartLengthAreBoundedBeforeBuildingCombinedAnswer() throws Exception {
        String source = source();

        int count = source.indexOf("int count = this.packet.readInt()");
        int answers = source.indexOf("String answers = this.packet.readString()", count);
        int guard = source.indexOf("count <= 0 || count > MAX_ANSWER_COUNT", answers);
        int builder = source.indexOf("StringBuilder answer = new StringBuilder()", guard);
        int loop = source.indexOf("for (int i = 0; i < count; i++)", builder);

        assertTrue(source.contains("MAX_ANSWER_COUNT = 20"),
                "Poll answers should have a bounded answer count");
        assertTrue(source.contains("MAX_ANSWER_PART_LENGTH = 255"),
                "Poll answer fragments should have a bounded length");
        assertTrue(source.contains("MAX_COMBINED_ANSWER_LENGTH = 2048"),
                "Poll combined answer should have a bounded final length");
        assertTrue(count > -1 && answers > count, "Poll handler must read count and answer string");
        assertTrue(guard > answers, "Poll handler must validate count and answer string after reading them");
        assertTrue(guard < builder && builder < loop,
                "Poll handler must validate inputs before building the repeated answer string");
    }

    @Test
    void combinedAnswerLengthIsCheckedBeforeWordQuizOrDatabaseWrite() throws Exception {
        String source = source();

        int append = source.indexOf("answer.append(\":\").append(answers)");
        int combinedGuard = source.indexOf("answer.length() > MAX_COMBINED_ANSWER_LENGTH", append);
        int wordQuiz = source.indexOf("handleWordQuiz", combinedGuard);
        int dbWrite = source.indexOf("INSERT INTO polls_answers", combinedGuard);

        assertTrue(combinedGuard > append,
                "Poll handler must check combined answer length while building it");
        assertTrue(combinedGuard < wordQuiz,
                "Poll handler must bound word quiz answers before dispatching them");
        assertTrue(combinedGuard < dbWrite,
                "Poll handler must bound poll answers before persisting them");
    }
}
