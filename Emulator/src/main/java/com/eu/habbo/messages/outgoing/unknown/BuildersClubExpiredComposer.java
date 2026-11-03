package com.eu.habbo.messages.outgoing.unknown;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.catalog.BuildersClubSubscriptionStatusComposer;

/**
 * Header 1452 is sent by {@link BuildersClubSubscriptionStatusComposer}. This class wrote five ints where the client
 * reads four ints and two booleans; it now sends that composer's layout with the values it always sent, and stays only
 * for plugins that may still use it.
 */
@Deprecated
public class BuildersClubExpiredComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        return new BuildersClubSubscriptionStatusComposer(Integer.MAX_VALUE, 0, 100, Integer.MAX_VALUE, false, false)
                .compose();
    }
}
