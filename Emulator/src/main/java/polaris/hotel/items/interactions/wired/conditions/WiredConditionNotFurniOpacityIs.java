package polaris.hotel.items.interactions.wired.conditions;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.items.Item;

/** "Furni opacity is not" ({@code wf_cnd_not_furni_opacity_is}): passes when no resolved furni compares as asked. */
public class WiredConditionNotFurniOpacityIs extends WiredConditionFurniOpacityIs {

    public WiredConditionNotFurniOpacityIs(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionNotFurniOpacityIs(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    protected boolean matchesItem(int shown) {
        return !super.matchesItem(shown);
    }
}
