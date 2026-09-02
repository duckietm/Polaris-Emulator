package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import java.sql.ResultSet;
import java.sql.SQLException;

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
    public boolean matches(HabboItem triggerItem, WiredEvent event) {
        return event != null && event.getType() == WiredEvent.Type.TEAM_LOSES;
    }
}
