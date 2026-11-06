package polaris.hotel.items.interactions.wired.effects;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.items.Item;
import polaris.hotel.wired.WiredEffectType;

/**
 * Server-side log that runs only when the stack's conditions FAIL (the negative branch). Identical to
 * {@link WiredEffectLog}, and it answers the same {@link WiredEffectType#WRITE_TO_LOGS} type because
 * the type code only picks the client dialog. WiredEffectPlanner recognises the negative branch by
 * this class, not by the type.
 */
public class WiredEffectNegativeLog extends WiredEffectLog {
    public static final WiredEffectType type = WiredEffectType.WRITE_TO_LOGS;

    public WiredEffectNegativeLog(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectNegativeLog(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }
}
