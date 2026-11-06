package polaris.hotel.items.interactions.wired.triggers;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.items.Item;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.WiredTriggerType;
import polaris.hotel.wired.core.WiredEvent;

public class WiredTriggerTeamLoses extends WiredTriggerGameStarts {
    public WiredTriggerTeamLoses(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredTriggerTeamLoses(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredTriggerType getType() {
        return WiredTriggerType.TEAM_GAME_RESULT;
    }
    /**
     * Both team-result triggers report {@link WiredTriggerType#TEAM_GAME_RESULT}, so
     * {@code RoomSpecialTypes.getTriggers} hands the engine the same set for either event and the
     * inherited {@code matches} accepts everything. Without this check a room's "loses" wired ran on
     * team wins as well.
     */
    @Override
    public boolean matches(UserItem triggerItem, WiredEvent event) {
        return event != null && event.getType() == WiredEvent.Type.TEAM_LOSES;
    }
}
