package com.eu.habbo.messages.outgoing.snowwar;

import com.eu.habbo.habbohotel.games.snowwar.SnowWarGame;
import com.eu.habbo.habbohotel.games.snowwar.SnowWarGamePlayer;
import com.eu.habbo.habbohotel.games.snowwar.mapping.SnowWarItem;
import com.eu.habbo.habbohotel.games.snowwar.objects.SnowWarMachineObject;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.List;

/**
 * Map + players + machines for the arena (PROTOCOL.md 5021).
 */
public class SnowStormLevelDataComposer extends MessageComposer {

    private final SnowWarGame game;
    private final boolean canEditRoom;

    public SnowStormLevelDataComposer(SnowWarGame game, boolean canEditRoom) {
        this.game = game;
        this.canEditRoom = canEditRoom;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.SnowStormLevelDataComposer);

        List<SnowWarGamePlayer> players = this.game.getActivePlayers();

        this.response.appendInt(this.game.getGameLengthSeconds());
        this.response.appendInt(this.game.getMapId());
        this.response.appendInt(this.game.getTeamCount());
        this.response.appendInt(players.size());

        for (SnowWarGamePlayer player : players) {
            this.response.appendInt(player.getObjectId());
            this.response.appendInt(player.getUserId());
            this.response.appendInt(player.getTeamId());
            this.response.appendString(player.getHabbo().getHabboInfo().getUsername());
            this.response.appendString(player.getHabbo().getHabboInfo().getLook());
            this.response.appendString(player.getHabbo().getHabboInfo().getGender().name().toUpperCase());
        }

        this.response.appendString(this.game.getMap().getHeightmapForPacket());

        List<SnowWarItem> items = this.game.getMap().getVisibleItems();
        this.response.appendInt(items.size());

        for (SnowWarItem item : items) {
            this.response.appendString(item.getName());
            this.response.appendInt(item.getX());
            this.response.appendInt(item.getY());
            this.response.appendInt(item.getRotation());
            // Room-ad image URL (empty for normal props) so the arena can
            // draw ad backgrounds saved by the editor.
            this.response.appendString(item.getImageUrl());
        }

        List<SnowWarMachineObject> machines = this.game.getMachines();
        this.response.appendInt(machines.size());

        for (SnowWarMachineObject machine : machines) {
            this.response.appendInt(machine.getObjectId());
            this.response.appendInt(machine.getX());
            this.response.appendInt(machine.getY());
        }

        // Trailing per-recipient flag: may this user open the arena editor
        // (acc_snowwar_edit)? Old clients simply don't read it.
        this.response.appendBoolean(this.canEditRoom);

        return this.response;
    }
}
