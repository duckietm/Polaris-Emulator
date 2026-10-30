package com.eu.habbo.messages.incoming.polls;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** A poll answer was stored, and the badge given, for any question id the client sent. */
class AnswerPollQuestionContractTest {

    private static final Path EVENT = Path.of("src/main/java/com/eu/habbo/messages/incoming/polls/AnswerPollEvent.java");
    private static final Path POLL = Path.of("src/main/java/com/eu/habbo/habbohotel/polls/Poll.java");

    @Test
    void onlyAQuestionOfThePollIsAnswered() throws Exception {
        String event = Files.readString(EVENT);
        String poll = Files.readString(POLL);

        int check = event.indexOf("poll.hasQuestion(questionId)");
        assertTrue(check > 0 && check < event.indexOf("INSERT INTO polls_answers"));
        assertTrue(check < event.indexOf("poll.lastQuestionId == questionId"));
        assertTrue(poll.contains("q.id == id || q.hasSubQuestion(id)"), "sub-questions count as the poll's own");
    }
}
