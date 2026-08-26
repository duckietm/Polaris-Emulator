package com.eu.habbo.habbohotel.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CommandsCommandFormatTest {

    @Test
    void keepsTheUsageLineOnItsOwnWhenNoHelpTextExists() {
        assertEquals(
                ":alert <username> <message>\r",
                CommandsCommand.formatEntry(":alert", ":alert <username> <message>", ""));
    }

    @Test
    void putsTheHelpTextUnderTheUsageLine() {
        assertEquals(
                ":alert <username> <message>\rSends a hotel alert to one user.\r",
                CommandsCommand.formatEntry(
                        ":alert", ":alert <username> <message>", "Sends a hotel alert to one user."));
    }

    @Test
    void describesACommandThatOnlyHasHelpText() {
        assertEquals(
                ":bots\rLists the bots placed in the room.\r",
                CommandsCommand.formatEntry(":bots", "", "Lists the bots placed in the room."));
    }

    @Test
    void keepsADescriptionAlreadyWrittenAsProseInsteadOfTheHelpText() {
        assertEquals(
                ":tradelock\rEnables / Disables the tradelock for a user.\r",
                CommandsCommand.formatEntry(
                        ":tradelock", "Enables / Disables the tradelock for a user.", "Something else entirely."));
    }

    @Test
    void fallsBackToThePlainCommandWhenNoTextExists() {
        assertEquals(":coords\r", CommandsCommand.formatEntry(":coords", "", ""));
    }
}
