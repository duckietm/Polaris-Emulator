package polaris.hotel.items.interactions.wired.extra;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import polaris.hotel.items.Item;
import polaris.hotel.wired.variablefx.WiredVariableFxStyles;

/**
 * The variable on this tile as a plain number, with an optional icon beside it. There is no range:
 * the client draws the value digit by digit. The category extra is where the icon goes, the string
 * param which icon. Registered on {@code wf_xtra_var_fx_number}.
 */
public class WiredExtraVariableFxNumberDisplay extends WiredExtraVariableFx {
    public static final int CODE = 135;

    private static final String[] ALIGNMENTS = {"left", "right", "double"};

    public WiredExtraVariableFxNumberDisplay(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraVariableFxNumberDisplay(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public int getCategory() {
        return WiredVariableFxStyles.CATEGORY_NUMBER_DISPLAY;
    }

    @Override
    public int getCode() {
        return CODE;
    }

    @Override
    protected boolean usesValueRange() {
        return false;
    }

    @Override
    protected void addCategoryExtra(Map<String, String> extra, WiredVariableFxStyles.Style style, int rendererId) {
        String icon = this.getIcon();
        if (!WiredVariableFxStyles.isKnownIcon(icon)) return;

        extra.put(WiredVariableFxStyles.EXTRA_ICON, icon);
        extra.put(
                WiredVariableFxStyles.EXTRA_ICON_ALIGNMENT,
                ALIGNMENTS[clamp(this.getCategoryExtra(), 0, ALIGNMENTS.length - 1)]);
    }
}
