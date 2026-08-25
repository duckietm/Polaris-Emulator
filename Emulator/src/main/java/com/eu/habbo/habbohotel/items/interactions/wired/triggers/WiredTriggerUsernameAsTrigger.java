package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Trigger furni {@code wf_trg_username_as_trigger} ("say your username").
 * <p>
 * Unlike {@link WiredTriggerHabboSaysKeyword}, where the owner configures a single static keyword
 * that every user must say, this trigger fires when a user says <em>their own username</em>. The
 * keyword is therefore dynamic per-actor: it is resolved at match time from the speaking habbo's
 * username rather than from an owner-supplied string.
 * </p>
 * <p>
 * It extends {@link WiredTriggerHabboSaysKeyword} so it reuses the existing
 * {@code SAY_SOMETHING} (code 0) client dialog, serialization and persistence. Only {@link #matches}
 * is overridden to compare the chat text against the actor's username; the owner-only gate is
 * inherited via {@link #isOwnerOnly()}.
 * </p>
 */
public class WiredTriggerUsernameAsTrigger extends WiredTriggerHabboSaysKeyword {

    public WiredTriggerUsernameAsTrigger(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredTriggerUsernameAsTrigger(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean matches(HabboItem triggerItem, WiredEvent event) {
        String text = event.getText().orElse(null);
        RoomUnit roomUnit = event.getActor().orElse(null);
        Room room = event.getRoom();

        if (text == null || roomUnit == null || room == null) {
            return false;
        }

        Habbo habbo = room.getHabbo(roomUnit);
        if (habbo == null) {
            return false;
        }

        if (this.isOwnerOnly() && room.getOwnerId() != habbo.getHabboInfo().getId()) {
            return false;
        }

        String username = habbo.getHabboInfo().getUsername();
        if (username == null || username.isEmpty()) {
            return false;
        }

        return text.toLowerCase().trim().contains(username.toLowerCase().trim());
    }
}
