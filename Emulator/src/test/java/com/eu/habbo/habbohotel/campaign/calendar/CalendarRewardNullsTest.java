package com.eu.habbo.habbohotel.campaign.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;

/** A reward without a badge, or whose furni could not be created, threw after the calendar day was claimed. */
class CalendarRewardNullsTest {

    @Test
    void aRewardWithoutABadgeReadsAsAnEmptyBadge() throws Exception {
        ResultSet set = mock(ResultSet.class);
        when(set.getString(anyString())).thenReturn(null);

        assertEquals("", new CalendarRewardObject(set).getBadge());
    }

    @Test
    void aFurniThatCouldNotBeCreatedIsNotSent() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/eu/habbo/habbohotel/campaign/calendar/CalendarRewardObject.java"));
        int create = source.indexOf(".createItem(habbo.getHabboInfo().getId(), item, 0, 0, \"\");");

        assertTrue(source.indexOf("if (habboItem == null) return;", create) > create);
        assertTrue(source.indexOf("if (habboItem == null) return;", create)
                < source.indexOf("new AddHabboItemComposer(habboItem)", create));
    }
}
