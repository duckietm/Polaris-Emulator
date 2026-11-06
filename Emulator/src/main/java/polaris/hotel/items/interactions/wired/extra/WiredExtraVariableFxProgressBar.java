package polaris.hotel.items.interactions.wired.extra;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.items.Item;
import polaris.hotel.wired.variablefx.WiredVariableFxStyles;

/** A plain progress bar of the variable on this tile between its minimum and maximum. Registered on {@code wf_xtra_var_fx_progress}. */
public class WiredExtraVariableFxProgressBar extends WiredExtraVariableFx {
    public static final int CODE = 131;

    public WiredExtraVariableFxProgressBar(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraVariableFxProgressBar(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public int getCategory() {
        return WiredVariableFxStyles.CATEGORY_PROGRESS_BAR;
    }

    @Override
    public int getCode() {
        return CODE;
    }
}
