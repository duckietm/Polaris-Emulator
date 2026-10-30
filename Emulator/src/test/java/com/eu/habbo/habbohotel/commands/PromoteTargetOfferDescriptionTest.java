package com.eu.habbo.habbohotel.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** `:promoteoffer info` cut every description at 25 characters and threw on a shorter one. */
class PromoteTargetOfferDescriptionTest {

    @Test
    void aShortDescriptionIsListedWholeAndALongOneIsCut() {
        assertEquals("Summer sale", PromoteTargetOfferCommand.listedDescription("Summer sale"));
        assertEquals("abcdefghijklmnopqrstuvwxy", PromoteTargetOfferCommand.listedDescription("abcdefghijklmnopqrstuvwxyz"));
        assertEquals("", PromoteTargetOfferCommand.listedDescription(null));
    }
}
