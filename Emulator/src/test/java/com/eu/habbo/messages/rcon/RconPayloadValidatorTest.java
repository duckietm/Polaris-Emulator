package com.eu.habbo.messages.rcon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RconPayloadValidatorTest {
    @Test
    void acceptsValidAnnotatedPayloads() {
        SetRank.JSONSetRank payload = new SetRank.JSONSetRank();
        payload.user_id = 1;
        payload.rank = 2;

        assertNull(RconPayloadValidator.validate(payload));
    }

    @Test
    void rejectsInvalidSetRankPayloadsBeforeDispatch() {
        SetRank.JSONSetRank payload = new SetRank.JSONSetRank();
        payload.user_id = 0;
        payload.rank = 2;

        assertEquals("invalid user", RconPayloadValidator.validate(payload));
    }

    @Test
    void rejectsInvalidGrantPayloadsBeforeDispatch() {
        GiveCredits.JSONGiveCredits payload = new GiveCredits.JSONGiveCredits();
        payload.user_id = 1;
        payload.credits = 0;

        assertEquals("invalid credits", RconPayloadValidator.validate(payload));
    }

    @Test
    void rejectsBlankBadgePayloadsBeforeDispatch() {
        GiveBadge.GiveBadgeJSON payload = new GiveBadge.GiveBadgeJSON();
        payload.user_id = 1;
        payload.badge = " ";

        assertEquals("invalid badge", RconPayloadValidator.validate(payload));
    }

    @Test
    void rejectsInvalidRoomOwnerIdentifiersBeforeDispatch() {
        ChangeRoomOwner.JSON payload = new ChangeRoomOwner.JSON();
        payload.room_id = 0;
        payload.user_id = 1;

        assertEquals("invalid room", RconPayloadValidator.validate(payload));
    }

    @Test
    void rejectsBlankStaffAlertsBeforeDispatch() {
        StaffAlert.JSON payload = new StaffAlert.JSON();
        payload.message = " \r\n ";

        assertEquals("invalid message", RconPayloadValidator.validate(payload));
    }

    @Test
    void rejectsInvalidSubscriptionMetadataBeforeDispatch() {
        ModifyUserSubscription.JSON payload = new ModifyUserSubscription.JSON();
        payload.user_id = 1;
        payload.type = "HABBO\nCLUB";
        payload.action = "add";
        payload.duration = 60;

        assertEquals("invalid subscription type", RconPayloadValidator.validate(payload));
    }

    @Test
    void rejectsInvalidUpdateUserToggleBeforeDispatch() {
        UpdateUser.JSON payload = new UpdateUser.JSON();
        payload.user_id = 1;
        payload.block_following = 2;

        assertEquals("invalid block_following", RconPayloadValidator.validate(payload));
    }
}
