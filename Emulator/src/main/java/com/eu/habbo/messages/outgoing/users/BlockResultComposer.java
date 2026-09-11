package com.eu.habbo.messages.outgoing.users;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * Official {@code class_2322} / {@code class_3781} (header 366): the result of a block change.
 * {@code BlockedUsersManager.onBlockUpdate} reads 0 as unblocked and 1 as blocked and shows the
 * matching {@code notification.blocked_player} / {@code notification.unblocked_player} bubble.
 */
public class BlockResultComposer extends MessageComposer {
    public static final int UNBLOCKED = 0;
    public static final int BLOCKED = 1;

    private final int result;
    private final int userId;

    public BlockResultComposer(int result, int userId) {
        this.result = result;
        this.userId = userId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.BlockResultComposer);
        this.response.appendInt(this.result);
        this.response.appendInt(this.userId);
        return this.response;
    }
}
