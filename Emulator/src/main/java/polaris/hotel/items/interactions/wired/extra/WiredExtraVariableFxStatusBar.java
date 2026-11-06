package polaris.hotel.items.interactions.wired.extra;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.items.Item;
import polaris.hotel.wired.variablefx.WiredVariableFxStyles;

/** A themed bar (energy, mana, cooldown...) of the variable on this tile; the style brings the icon and colour. Registered on {@code wf_xtra_var_fx_status}. */
public class WiredExtraVariableFxStatusBar extends WiredExtraVariableFx {
    public static final int CODE = 133;

    public WiredExtraVariableFxStatusBar(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraVariableFxStatusBar(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public int getCategory() {
        return WiredVariableFxStyles.CATEGORY_STATUS_BAR;
    }

    @Override
    public int getCode() {
        return CODE;
    }
}
