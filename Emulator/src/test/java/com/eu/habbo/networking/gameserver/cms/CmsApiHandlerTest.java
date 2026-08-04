package com.eu.habbo.networking.gameserver.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.messages.command.CommandResult;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.jupiter.api.Test;

class CmsApiHandlerTest {

    @Test
    void mapsCommandResultsToHttpStatuses() {
        assertEquals(HttpResponseStatus.OK, CmsApiHandler.httpStatusFor(new CommandResult(true, 0, "")));
        assertEquals(
                HttpResponseStatus.BAD_REQUEST,
                CmsApiHandler.httpStatusFor(new CommandResult(true, 1, "invalid credits")));
        assertEquals(
                HttpResponseStatus.NOT_FOUND,
                CmsApiHandler.httpStatusFor(new CommandResult(true, 2, "user not found")));
        assertEquals(
                HttpResponseStatus.NOT_FOUND,
                CmsApiHandler.httpStatusFor(new CommandResult(true, 3, "room not found")));
        assertEquals(
                HttpResponseStatus.INTERNAL_SERVER_ERROR, CmsApiHandler.httpStatusFor(new CommandResult(true, 4, "")));
        assertEquals(
                HttpResponseStatus.NOT_FOUND,
                CmsApiHandler.httpStatusFor(new CommandResult(false, 1, "unknown command")));
    }

    @Test
    void defaultAllowListAdmitsOnlyLoopback() {
        // With no Emulator config loaded, isAddressAllowed falls back to the
        // documented default of loopback-only.
        assertTrue(CmsApiHandler.isAddressAllowed("127.0.0.1"));
        assertTrue(CmsApiHandler.isAddressAllowed("::1"));
        assertFalse(CmsApiHandler.isAddressAllowed("8.8.8.8"));
        assertFalse(CmsApiHandler.isAddressAllowed(""));
        assertFalse(CmsApiHandler.isAddressAllowed(null));
    }
}
