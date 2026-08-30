package com.eu.habbo.habbohotel.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class EventCommandNotificationTest {

    @Test
    void joinsEveryWordAfterTheCommandIntoTheMessage() {
        assertEquals(
                "festa in piscina alle 21",
                EventCommand.joinMessage(new String[] {":event", "festa", "in", "piscina", "alle", "21"}));
    }

    @Test
    void reportsAnEmptyMessageWhenOnlyTheCommandWasTyped() {
        assertEquals("", EventCommand.joinMessage(new String[] {":event"}));
        assertEquals("", EventCommand.joinMessage(new String[] {":event", "   "}));
    }

    @Test
    void recognisesTheClosingArgumentRegardlessOfCase() {
        assertTrue(EventCommand.isClosingArgument(new String[] {":event", "off"}));
        assertTrue(EventCommand.isClosingArgument(new String[] {":event", "OFF"}));
        assertTrue(EventCommand.isClosingArgument(new String[] {":event", "stop"}));
    }

    @Test
    void treatsAnythingElseAsTheEventMessage() {
        assertFalse(EventCommand.isClosingArgument(new String[] {":event", "offerta", "di", "lavoro"}));
        assertFalse(EventCommand.isClosingArgument(new String[] {":event", "off", "topic"}));
        assertFalse(EventCommand.isClosingArgument(new String[] {":event"}));
    }

    @Test
    void sendsTheRoomLinkAndTheAutoCloseDelayWithAnOpeningAnnouncement() {
        Map<String, String> keys =
                EventCommand.notificationKeys("Piazza", 47, "Simo", "hd-180-1", "16:43", "festa in piscina", true);

        // Bare prefix: the client's link trackers do not know the official "event:" scheme.
        assertEquals("navigator/goto/47", keys.get("linkUrl"));
        assertEquals("notification.hotel.event.linkTitle", keys.get("linkTitle"));
        assertEquals("120", keys.get("timeout"));
    }

    @Test
    void leavesTheClosingAnnouncementWithoutALinkButStillClosesItself() {
        Map<String, String> keys = EventCommand.notificationKeys("Piazza", 47, "Simo", "hd-180-1", "16:43", "", false);

        assertNull(keys.get("linkUrl"));
        assertNull(keys.get("linkTitle"));
        assertEquals("120", keys.get("timeout"));
    }

    @Test
    void carriesTheRoomAndSenderDetailsTheTextsInterpolate() {
        Map<String, String> keys =
                EventCommand.notificationKeys("Piazza", 47, "Simo", "hd-180-1", "16:43", "festa in piscina", true);

        assertEquals("Piazza", keys.get("ROOMNAME"));
        assertEquals("47", keys.get("ROOMID"));
        assertEquals("Simo", keys.get("USERNAME"));
        assertEquals("hd-180-1", keys.get("LOOK"));
        assertEquals("16:43", keys.get("TIME"));
        assertEquals("festa in piscina", keys.get("MESSAGE"));
    }
}
