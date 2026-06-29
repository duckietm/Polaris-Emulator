package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestStorage;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.List;

/**
 * Wired chest (Scrigno) transaction log, newest first. Wire layout:
 * {@code int itemId, int rowCount, [string type, int epochSeconds, string userName, int withdrawn, int deposited]*}.
 */
public class ChestLogComposer extends MessageComposer {
    private final InteractionWiredChest chest;

    public ChestLogComposer(InteractionWiredChest chest) {
        this.chest = chest;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ChestLogComposer);
        this.response.appendInt(this.chest.getId());

        List<ChestStorage.LogEntry> log = this.chest.getContents().getLog();
        this.response.appendInt(log.size());

        for (ChestStorage.LogEntry e : log) {
            this.response.appendString(e.type == null ? "" : e.type);
            this.response.appendInt((int) (e.timestamp / 1000L));
            this.response.appendString(e.userName == null ? "" : e.userName);
            this.response.appendInt(e.withdrawn);
            this.response.appendInt(e.deposited);
        }

        return this.response;
    }
}
