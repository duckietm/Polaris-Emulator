package com.eu.habbo.habbohotel.games.freeze;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.games.*;
import com.eu.habbo.habbohotel.items.interactions.games.freeze.InteractionFreezeBlock;
import com.eu.habbo.habbohotel.items.interactions.games.freeze.InteractionFreezeExitTile;
import com.eu.habbo.habbohotel.items.interactions.games.freeze.InteractionFreezeTile;
import com.eu.habbo.habbohotel.items.interactions.games.freeze.gates.InteractionFreezeGate;
import com.eu.habbo.habbohotel.items.interactions.games.freeze.scoreboards.InteractionFreezeScoreboard;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTeleport;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUserAction;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserActionComposer;
import com.eu.habbo.plugin.EventHandler;
import com.eu.habbo.plugin.events.emulator.EmulatorConfigUpdatedEvent;
import com.eu.habbo.threading.runnables.freeze.FreezeClearEffects;
import com.eu.habbo.threading.runnables.freeze.FreezeThrowSnowball;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class FreezeGame extends Game {
    private static final Logger LOGGER = LoggerFactory.getLogger(FreezeGame.class);

    public static final int effectId = 39;

    public static int POWER_UP_POINTS;
    public static int POWER_UP_CHANCE;
    public static int POWER_UP_PROTECT_TIME;
    public static int DESTROY_BLOCK_POINTS;
    public static int FREEZE_TIME;
    public static int FREEZE_LOOSE_SNOWBALL;
    public static int FREEZE_LOOSE_BOOST;
    public static int MAX_LIVES;
    public static int MAX_SNOWBALLS;
    public static int FREEZE_LOOSE_POINTS;
    public static boolean POWERUP_STACK;

    public FreezeGame(Room room) {
        super(FreezeGameTeam.class, FreezeGamePlayer.class, room, true);
        room.setAllowEffects(true);
    }

    @EventHandler
    public static void onConfigurationUpdated(EmulatorConfigUpdatedEvent event) {
        POWER_UP_POINTS = Emulator.getConfig().getInt("hotel.freeze.points.effect");
        POWER_UP_CHANCE = Emulator.getConfig().getInt("hotel.freeze.powerup.chance");
        POWER_UP_PROTECT_TIME = Emulator.getConfig().getInt("hotel.freeze.powerup.protection.time");
        DESTROY_BLOCK_POINTS = Emulator.getConfig().getInt("hotel.freeze.points.block");
        FREEZE_TIME = Emulator.getConfig().getInt("hotel.freeze.onfreeze.time.frozen");
        FREEZE_LOOSE_SNOWBALL = Emulator.getConfig().getInt("hotel.freeze.onfreeze.loose.snowballs");
        FREEZE_LOOSE_BOOST = Emulator.getConfig().getInt("hotel.freeze.onfreeze.loose.explosionboost");
        MAX_LIVES = Emulator.getConfig().getInt("hotel.freeze.powerup.max.lives");
        MAX_SNOWBALLS = Emulator.getConfig().getInt("hotel.freeze.powerup.max.snowballs");
        FREEZE_LOOSE_POINTS = Emulator.getConfig().getInt("hotel.freeze.points.freeze");
        POWERUP_STACK = Emulator.getConfig().getBoolean("hotel.freeze.powerup.protection.stack");
    }

    @Override
    public synchronized void initialise() {
        if (this.state == GameState.RUNNING)
            return;

        this.resetMap();

        for (GameTeam t : this.teams.values()) {
            t.initialise();
        }

        this.start();
    }

    synchronized void resetMap() {
        for (HabboItem item : this.room.getFloorItems()) {
            if (item instanceof InteractionFreezeBlock || item instanceof InteractionFreezeScoreboard) {
                item.setExtradata("0");
                this.room.updateItemState(item);
            }
        }
    }

    public void throwBall(Habbo habbo, InteractionFreezeTile item) {
        if (!this.state.equals(GameState.RUNNING) || !habbo.getHabboInfo().isInGame() || habbo.getHabboInfo().getCurrentGame() != this.getClass())
            return;

        if (!item.getExtradata().equalsIgnoreCase("0") && !item.getExtradata().isEmpty())
            return;

        if (RoomLayout.tilesAdjecent(habbo.getRoomUnit().getCurrentLocation(), this.room.getLayout().getTile(item.getX(), item.getY()))) {
            if (((FreezeGamePlayer) habbo.getHabboInfo().getGamePlayer()).canThrowSnowball()) {
                Emulator.getThreading().run(new FreezeThrowSnowball(habbo, item, this.room));
            }
        }
    }

    public Set<RoomTile> affectedTilesByExplosion(short x, short y, int radius) {
        Set<RoomTile> tiles = new HashSet<>();

        RoomTile t = this.room.getLayout().getTile(x, y);

        tiles.add(t);
        for (int rotatation = 0; rotatation < 8; rotatation += 2) {
            for (int j = 0; j < radius; j++) {
                t = this.room.getLayout().getTileInFront(this.room.getLayout().getTile(x, y), rotatation, j);

                if (t == null || t.x < 0 || t.y < 0 || t.x >= this.room.getLayout().getMapSizeX() || t.y >= this.room.getLayout().getMapSizeY())
                    continue;

                tiles.add(t);
            }
        }

        return tiles;
    }

    public Set<RoomTile> affectedTilesByExplosionDiagonal(short x, short y, int radius) {
        Set<RoomTile> tiles = new HashSet<>();

        for (int rotation = 1; rotation < 9; rotation += 2) {
            RoomTile t = this.room.getLayout().getTile(x, y);

            for (int j = 0; j < radius; j++) {
                t = this.room.getLayout().getTileInFront(this.room.getLayout().getTile(x, y), rotation, j);

                if (t != null) {
                    if (t.x < 0 || t.y < 0 || t.x >= this.room.getLayout().getMapSizeX() || t.y >= this.room.getLayout().getMapSizeY())
                        continue;

                    tiles.add(t);
                }
            }
        }

        return tiles;
    }

    public synchronized void explodeBox(InteractionFreezeBlock block, int delay) {
        int powerUp = 0;
        if (Emulator.getRandom().nextInt(100) + 1 <= FreezeGame.POWER_UP_CHANCE) {
            powerUp += Emulator.getRandom().nextInt(6) + 1;
        }

        block.setExtradata((powerUp + 1) + String.format("%3d", delay));

        this.room.updateItemState(block);
    }

    public synchronized void givePowerUp(FreezeGamePlayer player, int powerUpId) {
        player.addScore(FreezeGame.POWER_UP_POINTS);

        switch (powerUpId) {
            case 2: {
                player.increaseExplosion();
                break;
            }

            case 3: {
                player.addSnowball();
                break;
            }

            case 4: {
                player.nextDiagonal = true;
                break;
            }

            case 5: {
                player.nextHorizontal = true;
                player.nextDiagonal = true;
                player.tempMassiveExplosion = true;
                break;
            }

            case 6: {
                player.addLife();
                break;
            }

            case 7: {
                player.addProtection();
                break;
            }
        }
    }

    public synchronized void playerDies(GamePlayer player) {
        Emulator.getThreading().run(new FreezeClearEffects(player.getHabbo()), 1000);
        if (this.room.getRoomSpecialTypes().hasFreezeExitTile()) {
            InteractionFreezeExitTile tile = this.room.getRoomSpecialTypes().getRandomFreezeExitTile();
            tile.setExtradata("1");
            this.room.updateItemState(tile);
            this.room.teleportHabboToItem(player.getHabbo(), tile);
        }
        this.removeHabbo(player.getHabbo());
    }

    @Override
    public void start() {
        if (this.state != GameState.IDLE) {
            return;
        }

        super.start();

        if (this.room.getRoomSpecialTypes().hasFreezeExitTile()) {
            for (Habbo habbo : this.room.getHabbos()) {
                if (this.getTeamForHabbo(habbo) == null) {
                    for (HabboItem item : this.room.getItemsAt(habbo.getRoomUnit().getCurrentLocation())) {
                        if (item instanceof InteractionFreezeTile) {
                            HabboItem exitTile = this.room.getRoomSpecialTypes().getRandomFreezeExitTile();
                            WiredEffectTeleport.teleportUnitToTile(habbo.getRoomUnit(), this.room.getLayout().getTile(exitTile.getX(), exitTile.getY()));
                        }
                    }
                }
            }
        }

        this.refreshGates();

        this.setFreezeTileState("1");
        this.run();
    }

    @Override
    public synchronized void run() {
        try {
            if (this.state.equals(GameState.IDLE))
                return;

            Emulator.getThreading().run(this, 1000);

            if (this.state.equals(GameState.PAUSED)) return;

            for (GameTeam team : this.teams.values()) {
                for (GamePlayer player : team.getMembers()) {
                    ((FreezeGamePlayer) player).cycle();
                }

                int totalScore = team.getTotalScore();

                Map<Integer, InteractionFreezeScoreboard> scoreBoards = this.room.getRoomSpecialTypes().getFreezeScoreboards(team.teamColor);

                for (InteractionFreezeScoreboard scoreboard : scoreBoards.values()) {
                    if (scoreboard.getExtradata().isEmpty()) {
                        scoreboard.setExtradata("0");
                    }

                    int oldScore = Integer.parseInt(scoreboard.getExtradata());

                    if (oldScore == totalScore)
                        continue;

                    scoreboard.setExtradata(totalScore + "");
                    this.room.updateItemState(scoreboard);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }
    }

    @Override
    public void stop() {
        super.stop();

        GameTeam winningTeam = null;

        for (GameTeam team : this.teams.values()) {
            if (winningTeam == null || team.getTotalScore() > winningTeam.getTotalScore()) {
                winningTeam = team;
            }
        }

        for (GameTeam team : this.teams.values()) {
            Set<GamePlayer> players = new HashSet<>();

            players.addAll(team.getMembers());

            for (GamePlayer p : players) {
                if (p.getScoreAchievementValue() > 0) {
                    if (team.equals(winningTeam)) {
                        AchievementManager.progressAchievement(p.getHabbo(), Emulator.getGameEnvironment().getAchievementManager().getAchievement("FreezeWinner"), p.getScoreAchievementValue());
                        this.room.sendComposer(new RoomUserActionComposer(p.getHabbo().getRoomUnit(), RoomUserAction.WAVE).compose());
                    }

                    AchievementManager.progressAchievement(p.getHabbo(), Emulator.getGameEnvironment().getAchievementManager().getAchievement("FreezePlayer"));
                }
            }
        }

        Map<GameTeamColors, Integer> teamMemberCount = new HashMap<>();
        for (Map.Entry<GameTeamColors, GameTeam> teamEntry : this.teams.entrySet()) {
            teamMemberCount.put(teamEntry.getKey(), teamEntry.getValue().getMembers().size());
        }

        for (Map.Entry<Integer, InteractionFreezeGate> set : this.room.getRoomSpecialTypes().getFreezeGates().entrySet()) {
            if (teamMemberCount.containsKey(set.getValue().teamColor)) {
                int amount = Math.min(teamMemberCount.get(set.getValue().teamColor), 5);
                set.getValue().setExtradata(amount + "");
                teamMemberCount.put(set.getValue().teamColor, teamMemberCount.get(set.getValue().teamColor) - amount);
                this.room.updateItemState(set.getValue());
            }
        }

        this.refreshGates();

        this.setFreezeTileState("0");
    }

    public void setFreezeTileState(String state) {
        for (InteractionFreezeExitTile object : this.room.getRoomSpecialTypes().getFreezeExitTiles().values()) {
            object.setExtradata(state);
            this.room.updateItemState(object);
        }

    }

    private void refreshGates() {
        Set<RoomTile> tilesToUpdate = new HashSet<>();
        for (HabboItem item : this.room.getRoomSpecialTypes().getFreezeGates().values()) {
            tilesToUpdate.add(this.room.getLayout().getTile(item.getX(), item.getY()));
        }

        this.room.updateTiles(tilesToUpdate);
    }
}
