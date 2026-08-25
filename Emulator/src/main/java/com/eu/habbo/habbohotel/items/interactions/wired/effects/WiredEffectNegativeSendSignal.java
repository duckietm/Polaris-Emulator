package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredEffectNegativeSendSignal extends WiredEffectSendSignal {
    public static final WiredEffectType type = WiredEffectType.NEG_SEND_SIGNAL;

    public WiredEffectNegativeSendSignal(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectNegativeSendSignal(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    protected boolean dispatchSignalEvent(WiredEvent event) {
        return WiredManager.dispatchEffectTriggeredEvent(event);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }
}
