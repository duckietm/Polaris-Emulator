package polaris.hotel.items.interactions.wired.effects;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.items.Item;
import polaris.hotel.wired.WiredEffectType;
import polaris.hotel.wired.core.WiredEvent;
import polaris.hotel.wired.core.WiredManager;

public class WiredEffectNegativeSendSignal extends WiredEffectSendSignal {
    public static final WiredEffectType type = WiredEffectType.NEG_SEND_SIGNAL;

    public WiredEffectNegativeSendSignal(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectNegativeSendSignal(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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
