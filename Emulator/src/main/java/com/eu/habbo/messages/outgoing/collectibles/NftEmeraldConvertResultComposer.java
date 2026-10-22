package com.eu.habbo.messages.outgoing.collectibles;

import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** AIR 15 {@code NftEmeraldConvertResultMessageEvent} (official 3207, taken here: 9424): {@code stuffId, result}, sent when an emerald furni could not be redeemed. */
public class NftEmeraldConvertResultComposer extends MessageComposer {
    private final int stuffId;
    private final int result;

    public NftEmeraldConvertResultComposer(int stuffId, int result) {
        this.stuffId = stuffId;
        this.result = result;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.NftEmeraldConvertResultComposer);
        this.response.appendInt(BuildersClubItemIds.toWire(this.stuffId));
        this.response.appendShort(this.result);
        return this.response;
    }
}
