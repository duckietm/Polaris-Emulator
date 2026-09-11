package com.eu.habbo.messages.outgoing.habbicons;

import com.eu.habbo.habbohotel.habbicons.HabbiconService;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public final class UserHabbiconsComposer extends MessageComposer {
    private final HabbiconService.Snapshot snapshot;

    public UserHabbiconsComposer(HabbiconService.Snapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.UserHabbiconsComposer);
        this.response.appendInt((int) snapshot.items().values().stream()
                .filter(HabbiconService.Item::collected)
                .count());
        for (HabbiconService.Item item : snapshot.items().values()) {
            if (item.collected()) {
                this.response.appendInt(item.id());
                this.response.appendInt(item.state());
            }
        }
        this.response.appendInt(snapshot.recent().size());
        for (int id : snapshot.recent()) {
            this.response.appendInt(id);
        }
        return this.response;
    }
}
