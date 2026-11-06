package polaris.hotel.items.interactions.wired.extra;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.items.Item;
import polaris.hotel.wired.variablefx.WiredVariableFxStyles;

/** The wide boss health bar of the variable on this tile. Registered on {@code wf_xtra_var_fx_boss}. */
public class WiredExtraVariableFxBossBar extends WiredExtraVariableFx {
    public static final int CODE = 134;

    public WiredExtraVariableFxBossBar(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraVariableFxBossBar(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public int getCategory() {
        return WiredVariableFxStyles.CATEGORY_BOSS_BAR;
    }

    @Override
    public int getCode() {
        return CODE;
    }
}
