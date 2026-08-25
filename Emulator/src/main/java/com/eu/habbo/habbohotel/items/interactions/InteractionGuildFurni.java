package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.guilds.Guild;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

public class InteractionGuildFurni extends InteractionDefault {
    private int guildId;
    private static final Set<String> ROTATION_8_ITEMS = new HashSet<String>() {
        {
            this.add("gld_wall_tall");
        }
    };

    public InteractionGuildFurni(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.guildId = set.getInt("guild_id");
    }

    public InteractionGuildFurni(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.guildId = 0;
    }

    @Override
    public int getMaximumRotations() {
        if(ROTATION_8_ITEMS.stream().anyMatch(x -> x.equalsIgnoreCase(this.getBaseItem().getName()))) {
            return 8;
        }
        return this.getBaseItem().getRotations();
    }

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(this.guildId);

        if (guild != null) {
            serverMessage.appendInt(2 + (this.isLimited() ? 256 : 0));
            serverMessage.appendInt(5);
            serverMessage.appendString(this.getExtradata());
            serverMessage.appendString(guild.getId() + "");
            serverMessage.appendString(guild.getBadge());
            serverMessage.appendString(Emulator.getGameEnvironment().getGuildManager().getSymbolColor(guild.getColorOne()).valueA);
            serverMessage.appendString(Emulator.getGameEnvironment().getGuildManager().getBackgroundColor(guild.getColorTwo()).valueA);
        } else {
            serverMessage.appendInt((this.isLimited() ? 256 : 0));
            serverMessage.appendString(this.getExtradata());
        }

        if (this.isLimited()) {
            serverMessage.appendInt(this.getLimitedSells());
            serverMessage.appendInt(this.getLimitedStack());
        }
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return true;
    }

    @Override
    public boolean isWalkable() {
        return this.getBaseItem().allowWalk();
    }

    public int getGuildId() {
        return this.guildId;
    }

    public void setGuildId(int guildId) {
        this.guildId = guildId;
    }

    @Override
    public boolean allowWiredResetState() {
        return true;
    }
}
