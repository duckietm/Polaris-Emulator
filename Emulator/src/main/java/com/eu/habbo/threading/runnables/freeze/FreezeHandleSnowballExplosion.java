package com.eu.habbo.threading.runnables.freeze;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.games.freeze.FreezeGame;
import com.eu.habbo.habbohotel.games.freeze.FreezeGamePlayer;
import com.eu.habbo.habbohotel.items.interactions.games.freeze.InteractionFreezeBlock;
import com.eu.habbo.habbohotel.items.interactions.games.freeze.InteractionFreezeTile;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import gnu.trove.set.hash.THashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class FreezeHandleSnowballExplosion implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(FreezeHandleSnowballExplosion.class);

    private final FreezeThrowSnowball thrownData;

    public FreezeHandleSnowballExplosion(FreezeThrowSnowball thrownData) {
        this.thrownData = thrownData;
    }

    @Override
    public void run() {
        try {
            if (this.thrownData == null || this.thrownData.habbo.getHabboInfo().getGamePlayer() == null)
                return;

            FreezeGamePlayer player = (FreezeGamePlayer) this.thrownData.habbo.getHabboInfo().getGamePlayer();

            if (player == null)
                return;

            player.addSnowball();

            THashSet<RoomTile> tiles = new THashSet<>();

            FreezeGame game = ((FreezeGame) this.thrownData.room.getGame(FreezeGame.class));

            if (game == null)
                return;

            if (player.nextHorizontal) {
                tiles.addAll(game.affectedTilesByExplosion(this.thrownData.targetTile.getX(), this.thrownData.targetTile.getY(), this.thrownData.radius + 1));
            }

            if (player.nextDiagonal) {
                tiles.addAll(game.affectedTilesByExplosionDiagonal(this.thrownData.targetTile.getX(), this.thrownData.targetTile.getY(), this.thrownData.radius + 1));
                player.nextDiagonal = false;
            }

            THashSet<InteractionFreezeTile> freezeTiles = new THashSet<>();

            for (RoomTile roomTile : tiles) {
                THashSet<HabboItem> items = this.thrownData.room.getItemsAt(roomTile);

                // Track if we already processed a block at this tile to prevent stacking exploit
                boolean blockProcessedAtTile = false;

                for (HabboItem freezeTile : items) {
                    if (freezeTile instanceof InteractionFreezeTile || freezeTile instanceof InteractionFreezeBlock) {
                        int distance = 0;
                        if (freezeTile.getX() != this.thrownData.targetTile.getX() && freezeTile.getY() != this.thrownData.targetTile.getY()) {
                            distance = Math.abs(freezeTile.getX() - this.thrownData.targetTile.getX());
                        } else {
                            distance = (int) Math.ceil(this.thrownData.room.getLayout().getTile(this.thrownData.targetTile.getX(), this.thrownData.targetTile.getY()).distance(roomTile));
                        }

                        if (freezeTile instanceof InteractionFreezeTile freezeTileItem) {
                            freezeTile.setExtradata("11" + String.format("%03d", distance * 100)); //TODO Investigate this further. Probably height dependent or something.
                            freezeTiles.add(freezeTileItem);
                            this.thrownData.room.updateItem(freezeTile);


                            THashSet<Habbo> habbos = new THashSet<>();
                            habbos.addAll(this.thrownData.room.getHabbosAt(freezeTile.getX(), freezeTile.getY()));

                            for (Habbo habbo : habbos) {
                                if (habbo.getHabboInfo().getGamePlayer() instanceof FreezeGamePlayer hPlayer) {
                                    if (!hPlayer.canGetFrozen())
                                        continue;

                                    if (hPlayer.getTeamColor().equals(player.getTeamColor()))
                                        player.addScore(-FreezeGame.FREEZE_LOOSE_POINTS);
                                    else
                                        player.addScore(FreezeGame.FREEZE_LOOSE_POINTS);

                                    hPlayer.freeze();

                                    if (this.thrownData.habbo != habbo) {
                                        AchievementManager.progressAchievement(habbo, Emulator.getGameEnvironment().getAchievementManager().getAchievement("EsA"));
                                    }
                                }
                            }
                        } else if (freezeTile instanceof InteractionFreezeBlock freezeBlock) {
                            // Only process ONE block per tile to prevent stacking exploit
                            // Stacking many blocks and exploding them causes massive lag
                            if (!blockProcessedAtTile && freezeTile.getExtradata().equalsIgnoreCase("0")) {
                                game.explodeBox(freezeBlock, distance * 100);
                                player.addScore(FreezeGame.DESTROY_BLOCK_POINTS);
                                blockProcessedAtTile = true;
                            }
                        }
                    }
                }
            }

            Emulator.getThreading().run(new FreezeResetExplosionTiles(freezeTiles, this.thrownData.room), 1000);
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }
    }
}
