package com.eu.habbo.messages.outgoing.generic.alerts;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GenericErrorCodeContractTest {
    @Test
    void definesTheStableGenericErrorProtocolCodes() {
        assertEquals(-3, GenericErrorCode.AUTHENTICATION_FAILED.code());
        assertEquals(-400, GenericErrorCode.CONNECTING_TO_SERVER_FAILED.code());
        assertEquals(4008, GenericErrorCode.KICKED_OUT_OF_ROOM.code());
        assertEquals(4009, GenericErrorCode.VIP_REQUIRED.code());
        assertEquals(4010, GenericErrorCode.ROOM_NAME_UNACCEPTABLE.code());
        assertEquals(4011, GenericErrorCode.CANNOT_BAN_GROUP_MEMBER.code());
        assertEquals(-100002, GenericErrorCode.WRONG_ROOM_PASSWORD.code());
        assertEquals(-13001, GenericErrorCode.TRADE_STRIP_LOCKED.code());
    }

    @Test
    void protocolCodesAreUnique() {
        Set<Integer> uniqueCodes = Arrays.stream(GenericErrorCode.values())
                .map(GenericErrorCode::code)
                .collect(Collectors.toSet());

        assertEquals(GenericErrorCode.values().length, uniqueCodes.size());
    }

    @Test
    void composerAcceptsTheTypedProtocolCode() {
        GenericErrorMessagesComposer composer =
                new GenericErrorMessagesComposer(GenericErrorCode.WRONG_ROOM_PASSWORD);

        assertEquals(GenericErrorCode.WRONG_ROOM_PASSWORD.code(), composer.getErrorCode());
    }
}
