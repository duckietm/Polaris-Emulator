package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.Collection;

public class WiredFurniGravityComposer extends MessageComposer {
    private static final int STYLE_DEFAULT = 0;
    private static final int STYLE_DROP = 6;
    private static final int INTENSITY_MAX = 100;

    private final Collection<HabboItem> items;
    private final int gravity;

    public WiredFurniGravityComposer(Collection<HabboItem> items, int gravity) {
        this.items = items;
        this.gravity = gravity;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredFurniGravityComposer);
        this.response.appendInt(this.items.size());

        for (HabboItem item : this.items) {
            this.response.appendInt(item.getId());
        }

        this.response.appendInt(this.gravity > 0 ? STYLE_DROP : STYLE_DEFAULT);
        this.response.appendInt(this.gravity > 0 ? INTENSITY_MAX : 0);

        return this.response;
    }
}
