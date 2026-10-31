package com.eu.habbo.messages.incoming.polls;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.polls.Poll;
import com.eu.habbo.habbohotel.polls.PollQuestion;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The poll badge came with the last question's id alone; it now needs every main question answered. */
class AnswerPollBadgeTest {

    private static PollQuestion question(int id) throws SQLException {
        ResultSet set = mock(ResultSet.class);
        when(set.getInt("id")).thenReturn(id);
        when(set.getInt("type")).thenReturn(3);
        when(set.getString("options")).thenReturn("");
        return new PollQuestion(set);
    }

    private static Poll poll(int... ids) throws SQLException {
        List<PollQuestion> questions = new ArrayList<>();
        for (int id : ids) questions.add(question(id));

        Poll poll = mock(Poll.class);
        when(poll.getQuestions()).thenReturn(new ArrayList<>(questions));
        return poll;
    }

    @Test
    void everyMainQuestionAnsweredEarnsTheBadge() throws SQLException {
        assertTrue(AnswerPollEvent.hasAnsweredEveryQuestion(poll(1, 2, 3), Set.of(1, 2, 3, 40)));
    }

    @Test
    void onlyTheLastQuestionIsNotEnough() throws SQLException {
        assertFalse(AnswerPollEvent.hasAnsweredEveryQuestion(poll(1, 2, 3), Set.of(3)));
    }
}
