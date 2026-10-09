package com.eu.habbo.messages.incoming.polls;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PollAnswerInputGuardContractTest {
    private static String source() throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/polls/AnswerPollEvent.java"));
    }

    @Test
    void pollAnswerCountAndPartLengthAreBoundedBeforeBuildingCombinedAnswer() throws Exception {
        String source = source();

        int count = source.indexOf("int count = this.packet.readInt()");
        int guard = source.indexOf("count <= 0 || count > MAX_ANSWER_COUNT", count);
        int builder = source.indexOf("StringBuilder answer = new StringBuilder()", guard);
        int loop = source.indexOf("for (int i = 0; i < count; i++)", builder);
        int answers = source.indexOf("String answers = this.packet.readString()", loop);
        int partGuard = source.indexOf("answers.length() > MAX_ANSWER_PART_LENGTH", answers);

        assertTrue(source.contains("MAX_ANSWER_COUNT = 20"), "Poll answers should have a bounded answer count");
        assertTrue(
                source.contains("MAX_ANSWER_PART_LENGTH = 255"), "Poll answer fragments should have a bounded length");
        assertTrue(
                source.contains("MAX_COMBINED_ANSWER_LENGTH = 2048"),
                "Poll combined answer should have a bounded final length");
        assertTrue(count > -1 && guard > count, "Poll handler must validate the answer count after reading it");
        assertTrue(
                guard < builder && builder < loop,
                "Poll handler must validate the count before building the combined answer string");
        assertTrue(
                answers > loop && partGuard > answers,
                "Poll handler must read and bound every answer string inside the loop");
    }

    @Test
    void combinedAnswerLengthIsCheckedBeforeWordQuizOrDatabaseWrite() throws Exception {
        String source = source();

        int append = source.indexOf("answer.append(\":\").append(answers)");
        int combinedGuard = source.indexOf("answer.length() > MAX_COMBINED_ANSWER_LENGTH", append);
        int wordQuiz = source.indexOf("handleWordQuiz", combinedGuard);
        int dbWrite = source.indexOf("INSERT INTO polls_answers", combinedGuard);

        assertTrue(combinedGuard > append, "Poll handler must check combined answer length while building it");
        assertTrue(combinedGuard < wordQuiz, "Poll handler must bound word quiz answers before dispatching them");
        assertTrue(combinedGuard < dbWrite, "Poll handler must bound poll answers before persisting them");
    }
}
