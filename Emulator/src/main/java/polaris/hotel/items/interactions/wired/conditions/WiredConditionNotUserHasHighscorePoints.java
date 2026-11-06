package polaris.hotel.items.interactions.wired.conditions;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.items.Item;

/** "User does not have X points on the scoreboard" ({@code wf_cnd_not_x_points_leaderboard}): the comparison turned around, per user. */
public class WiredConditionNotUserHasHighscorePoints extends WiredConditionUserHasHighscorePoints {

    public WiredConditionNotUserHasHighscorePoints(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionNotUserHasHighscorePoints(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    protected boolean matchesTarget(int best) {
        return !super.matchesTarget(best);
    }
}
