package polaris.hotel.items.interactions.wired.conditions;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.items.Item;

/** "User does not have rank" ({@code wf_cnd_not_habbo_has_rank}): the comparison turned around, per user. */
public class WiredConditionNotUserHasRank extends WiredConditionUserHasRank {

    public WiredConditionNotUserHasRank(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionNotUserHasRank(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    protected boolean matchesTarget(int reached) {
        return !super.matchesTarget(reached);
    }
}
