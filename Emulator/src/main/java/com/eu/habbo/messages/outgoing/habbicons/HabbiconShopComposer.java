package com.eu.habbo.messages.outgoing.habbicons;

import com.eu.habbo.habbohotel.habbicons.HabbiconService;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public final class HabbiconShopComposer extends MessageComposer {
    private final HabbiconService.Snapshot snapshot;

    public HabbiconShopComposer(HabbiconService.Snapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.HabbiconShopComposer);
        this.response.appendInt(snapshot.collections().size());
        for (HabbiconService.Collection collection : snapshot.collections()) {
            this.response.appendInt(collection.id());
            this.response.appendString(collection.name());
            this.response.appendBoolean(collection.completed());
            this.response.appendInt(collection.rewardId());
            this.response.appendInt(collection.rewardState());
            this.response.appendInt(collection.credits());
            this.response.appendInt(collection.points());
            this.response.appendInt(collection.pointsType());
            this.response.appendInt(collection.items().size());
            for (HabbiconService.Item item : collection.items()) {
                HabbiconInfoComposer.appendItem(this.response, item);
            }
        }
        return this.response;
    }
}
