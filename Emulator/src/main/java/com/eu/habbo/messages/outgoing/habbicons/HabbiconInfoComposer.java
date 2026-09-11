package com.eu.habbo.messages.outgoing.habbicons;

import com.eu.habbo.habbohotel.habbicons.HabbiconService;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public final class HabbiconInfoComposer extends MessageComposer {
    private final HabbiconService.Item item;

    public HabbiconInfoComposer(HabbiconService.Item item) {
        this.item = item;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.HabbiconInfoComposer);
        appendItem(this.response, item);
        return this.response;
    }

    static void appendItem(ServerMessage message, HabbiconService.Item item) {
        message.appendInt(item.id());
        message.appendString(item.name());
        message.appendInt(item.collectionId());
        message.appendInt(item.state());
        message.appendInt(item.credits());
        message.appendInt(item.points());
        message.appendInt(item.pointsType());
    }
}
