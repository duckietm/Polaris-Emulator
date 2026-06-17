package com.eu.habbo.threading.runnables;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.rooms.items.FloorItemOnRollerComposer;
import com.eu.habbo.messages.outgoing.rooms.items.ItemStateComposer;
import com.eu.habbo.util.pathfinding.Direction8;
import gnu.trove.set.hash.THashSet;

public class RebugKickBallAction implements Runnable {

    private final HabboItem ball;
    private final Room room;
    private Direction8 direction;
    private int momentum;
    private boolean isDribble;
    public boolean dead = false;

    private final boolean zigzag;
    private Direction8 zigzagA;
    private Direction8 zigzagB;
    private boolean zigzagSide = false;

    private int tilesSinceBounce = -1;

    public RebugKickBallAction(HabboItem ball, Room room, Direction8 direction, int momentum) {
        this(ball, room, direction, momentum, false);
    }

    public RebugKickBallAction(HabboItem ball, Room room, Direction8 direction, int momentum, boolean zigzag) {
        this.ball = ball;
        this.room = room;
        this.direction = direction;
        this.momentum = momentum;
        this.isDribble = (momentum == 0);
        this.zigzag = zigzag && !this.isDribble;

        if (this.zigzag) {
            this.zigzagA = direction.rotateDirection45Degrees(false);
            this.zigzagB = direction.rotateDirection45Degrees(true);
        }
    }

    public boolean isDribble() {
        return this.isDribble;
    }

    private boolean isTileBlocked(int x, int y) {
        RoomTile tile = this.room.getLayout().getTile((short) x, (short) y);
        if (tile == null) return true;
        if (tile.getState() != RoomTileState.OPEN) return true;
        return x == this.room.getLayout().getDoorX() && y == this.room.getLayout().getDoorY();
    }

    @Override
    public void run() {
        if (this.dead || !this.room.isLoaded()) return;

        try {
            int nextX;
            int nextY;
            Direction8 moveDir;

            if (this.zigzag) {
                Direction8 preferred = this.zigzagSide ? this.zigzagB : this.zigzagA;
                Direction8 fallback  = this.zigzagSide ? this.zigzagA : this.zigzagB;

                nextX = this.ball.getX() + preferred.getDiffX();
                nextY = this.ball.getY() + preferred.getDiffY();

                if (isTileBlocked(nextX, nextY)) {
                    nextX = this.ball.getX() + fallback.getDiffX();
                    nextY = this.ball.getY() + fallback.getDiffY();

                    if (isTileBlocked(nextX, nextY)) {
                        nextX = this.ball.getX() + this.direction.getDiffX();
                        nextY = this.ball.getY() + this.direction.getDiffY();

                        if (isTileBlocked(nextX, nextY)) {
                            this.stopBall();
                            return;
                        }
                        moveDir = this.direction;
                    } else {
                        moveDir = fallback;
                      }
                } else {
                    moveDir = preferred;
                    this.zigzagSide = !this.zigzagSide;
                }
            } else {
                nextX = this.ball.getX() + this.direction.getDiffX();
                nextY = this.ball.getY() + this.direction.getDiffY();

                if (isTileBlocked(nextX, nextY)) {
                    int dx = this.direction.getDiffX();
                    int dy = this.direction.getDiffY();

                    if (dx != 0 && dy != 0) {
                        boolean xBlocked = isTileBlocked(this.ball.getX() + dx, this.ball.getY());
                        boolean yBlocked = isTileBlocked(this.ball.getX(), this.ball.getY() + dy);

                        if (xBlocked && !yBlocked) {
                            this.direction = Direction8.fromDelta(-dx, dy);
                        } else if (!xBlocked && yBlocked) {
                            this.direction = Direction8.fromDelta(dx, -dy);
                        } else {
                            this.direction = this.direction.rotateDirection180Degrees();
                        }
                    } else {
                         this.direction = this.direction.rotateDirection180Degrees();
                    }

                    this.tilesSinceBounce = 0;
                    nextX = this.ball.getX() + this.direction.getDiffX();
                    nextY = this.ball.getY() + this.direction.getDiffY();
                }
                moveDir = this.direction;
            }

            RoomTile nextTile = this.room.getLayout().getTile((short) nextX, (short) nextY);
            if (nextTile == null) {
                this.stopBall();
                return;
            }

            RoomTile oldTile = this.room.getLayout().getTile(this.ball.getX(), this.ball.getY());
            double oldZ = this.ball.getZ();

            this.ball.setRotation(moveDir.getRot());
            this.ball.setX(nextTile.x);
            this.ball.setY(nextTile.y);
            this.ball.setZ(nextTile.getStackHeight());
            this.ball.needsUpdate(true);

            if (!this.zigzag && this.tilesSinceBounce >= 0) {
                this.tilesSinceBounce++;
            }

            if (!this.zigzag && this.tilesSinceBounce > 1 && !this.isDribble) {
                THashSet<Habbo> habbos = this.room.getHabbosAt(nextTile.x, nextTile.y);
                if (!habbos.isEmpty()) {
                    this.direction = this.direction.rotateDirection180Degrees();
                    this.tilesSinceBounce = 0;
                }
            }

            this.ball.setExtradata(this.isDribble ? "2" : "5");
            this.room.sendComposer(new ItemStateComposer(this.ball).compose());

            this.momentum -= 11;

            if (!this.isDribble) {
                long delay = getDelayForMomentum(this.momentum);
                if (delay > 0) {
                    Emulator.getThreading().run(this, delay);
                } else {
                    this.stopBall();
                }
            } else {
                this.dead = true;
            }

            // updateTile() below removes both tiles from the item cache (rebuilt
            // lazily from the ball's already-updated position), so mutating the
            // shared cached THashSets here is both redundant and a data race
            // against the room-cycle/IO threads iterating those same sets.
            this.room.updateTile(oldTile);
            this.room.updateTile(nextTile);

            this.room.sendComposer(new FloorItemOnRollerComposer(
                    this.ball, null, oldTile, oldZ, nextTile, this.ball.getZ(), 0.0D, this.room
            ).compose());
        } catch (Exception e) {
            this.stopBall();
        }
    }

    private void stopBall() {
        this.dead = true;
        this.ball.setExtradata("0");
        this.room.sendComposer(new ItemStateComposer(this.ball).compose());
    }

    private long getDelayForMomentum(int momentum) {
        switch (momentum) {
            case 55: return 100L;
            case 44: return 100L;
            case 33: return 200L;
            case 22: return 250L;
            case 11: return 500L;
            default: return 0L;
        }
    }
}
