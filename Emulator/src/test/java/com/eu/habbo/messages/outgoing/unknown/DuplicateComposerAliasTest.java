package com.eu.habbo.messages.outgoing.unknown;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.handshake.IsFirstLoginOfDayComposer;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.catalog.BuildersClubSubscriptionStatusComposer;
import com.eu.habbo.messages.outgoing.handshake.DisconnectReasonComposer;
import org.junit.jupiter.api.Test;

/**
 * Three unused composers sent the same headers as the ones in use, and the packet contract was checked against them;
 * the Builders Club one even wrote a layout the client does not read. They stay for plugins and send the same bytes.
 */
@SuppressWarnings("deprecation")
class DuplicateComposerAliasTest {

    private static void assertSamePacket(MessageComposer expected, MessageComposer alias) {
        ServerMessage want = expected.compose();
        ServerMessage got = alias.compose();

        assertEquals(want.getHeader(), got.getHeader());
        assertEquals(want.getBodyString(), got.getBodyString());
    }

    @Test
    void theDisconnectAliasSendsTheDisconnectReason() {
        assertSamePacket(new DisconnectReasonComposer(12), new ErrorLoginComposer(12));
    }

    @Test
    void theFirstLoginAliasSendsFalse() {
        assertSamePacket(new IsFirstLoginOfDayComposer(false), new UnknownComposer4());
    }

    @Test
    void theBuildersClubAliasSendsTheSubscriptionLayout() {
        assertSamePacket(
                new BuildersClubSubscriptionStatusComposer(Integer.MAX_VALUE, 0, 100, Integer.MAX_VALUE, false, false),
                new BuildersClubExpiredComposer());
    }
}
