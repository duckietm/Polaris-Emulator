package polaris.messages.outgoing.habbicons;

import polaris.hotel.habbicons.HabbiconService;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public final class HabbiconShopDataComposer extends MessageComposer {
    private final HabbiconService.Snapshot snapshot;

    public HabbiconShopDataComposer(HabbiconService.Snapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.HabbiconShopDataComposer);
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
