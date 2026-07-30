package com.eu.habbo.messages.outgoing.snowwar;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.games.snowwar.mapping.SnowWarItem;
import com.eu.habbo.habbohotel.games.snowwar.mapping.SnowWarMap;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.List;

/**
 * Arena map handed to a user opening the editor straight from the queue (no
 * running game to supply it). Emits the same LevelData wire format (header
 * 5021) the game uses, with no players / machines and canEditRoom = true, so
 * the client's existing SnowWarLevelData parser and editor handle it unchanged.
 */
public class SnowStormEditorDataComposer extends MessageComposer {

    private final SnowWarMap map;
    private final String arenaName;

    public SnowStormEditorDataComposer(SnowWarMap map, String arenaName) {
        this.map = map;
        this.arenaName = arenaName;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.SnowStormLevelDataComposer);

        this.response.appendInt(Emulator.getConfig().getInt("gamecenter.snowwar.game.length.seconds", 180));
        this.response.appendInt(this.map.getMapId());
        this.response.appendInt(2); // teamCount
        this.response.appendInt(0); // no players in the editor

        this.response.appendString(this.map.getHeightmapForPacket());

        List<SnowWarItem> items = this.map.getVisibleItems();
        this.response.appendInt(items.size());

        for (SnowWarItem item : items) {
            this.response.appendString(item.getName());
            this.response.appendInt(item.getX());
            this.response.appendInt(item.getY());
            this.response.appendInt(item.getRotation());
            this.response.appendString(item.getImageUrl());
            this.response.appendInt(item.getOffsetZ());
            this.response.appendInt(item.getWalkableHeight());
            this.response.appendInt(item.getWidth());
            this.response.appendInt(item.getLength());
            this.response.appendInt(item.getState());
            this.response.appendInt(item.getStateCount());
        }

        this.response.appendInt(0); // no machines in the editor
        this.response.appendBoolean(true); // canEditRoom
        this.response.appendString(this.arenaName);

        return this.response;
    }
}
