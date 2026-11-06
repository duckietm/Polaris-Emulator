package polaris.messages.outgoing.snowwar;

import java.util.List;
import polaris.hotel.games.snowwar.SnowWarGame;
import polaris.hotel.games.snowwar.SnowWarGamePlayer;
import polaris.hotel.games.snowwar.mapping.SnowWarItem;
import polaris.hotel.games.snowwar.objects.SnowWarMachineObject;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

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
            this.response.appendString(player.getUser().getUserInfo().getUsername());
            this.response.appendString(player.getUser().getUserInfo().getLook());
            this.response.appendString(
                    player.getUser().getUserInfo().getGender().name().toUpperCase());
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
            // draw ad backgrounds saved by the editor, plus the vertical
            // offset that nudges that full-screen backdrop up/down.
            this.response.appendString(item.getImageUrl());
            this.response.appendInt(item.getOffsetZ());
            // Per-furni walkable height so the client blocks the same tiles the
            // server does (0 = walkable rug/tile, >0 = solid), instead of
            // treating every prop as impassable.
            this.response.appendInt(item.getWalkableHeight());
            // Furni footprint in tiles (unrotated width/length from furnidata).
            // The client rotates these the same way the server does to block the
            // whole footprint and depth-sort multi-tile props by their front
            // tile, so a 3x3 prop no longer draws over adjacent avatars.
            this.response.appendInt(item.getWidth());
            this.response.appendInt(item.getLength());
            // Multistate furni state index (0 = single-state) so the client draws
            // the furni in the state chosen in the editor.
            this.response.appendInt(item.getState());
            // Number of interaction states (items_base.interaction_modes_count) so
            // the editor caps the state stepper at the furni's real range.
            this.response.appendInt(item.getStateCount());
        }

        List<SnowWarMachineObject> machines = this.game.getMachines();
        this.response.appendInt(machines.size());

        for (SnowWarMachineObject machine : machines) {
            this.response.appendInt(machine.getObjectId());
            this.response.appendInt(machine.getX());
            this.response.appendInt(machine.getY());
        }

        // Trailing per-recipient flag: may this user open the arena editor
        // (acc_snowwar_arena_build)? Old clients simply don't read it.
        this.response.appendBoolean(this.canEditRoom);

        return this.response;
    }
}
