package polaris.threading.runnables;

import polaris.Emulator;
import polaris.hotel.items.interactions.InteractionPushable;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.rooms.RoomUserRotation;
import polaris.messages.outgoing.rooms.items.FloorItemOnRollerComposer;

public class KickBallAction implements Runnable {

    private final InteractionPushable ball; // The item which is moving
    private final Room room; // The room that the item belongs to
    private final RoomUnit kicker; // The Habbo which initiated the move of the item
    private final int totalSteps; // The total number of steps in the move sequence
    public boolean dead =
            false; // When true the run() function will not execute. Used when another user kicks the ball whilst it is
    // arleady moving.
    private RoomUserRotation currentDirection; // The current direction the item is moving in
    private int currentStep; // The current step of the move sequence
    public final boolean isDrag;

    public KickBallAction(
            InteractionPushable ball,
            Room room,
            RoomUnit kicker,
            RoomUserRotation direction,
            int steps,
            boolean isDrag) {
        this.ball = ball;
        this.room = room;
        this.kicker = kicker;
        this.currentDirection = direction;
        this.totalSteps = steps;
        this.currentStep = 0;
        this.isDrag = isDrag;
    }

    @Override
    public void run() {
        if (this.dead || !this.room.isLoaded()) return;

        if (this.currentStep < this.totalSteps) {
            RoomTile currentTile = this.room.getLayout().getTile(this.ball.getX(), this.ball.getY());
            RoomTile next = this.room.getLayout().getTileInFront(currentTile, this.currentDirection.getValue());

            if (next == null
                    || !this.ball.validMove(
                            this.room, this.room.getLayout().getTile(this.ball.getX(), this.ball.getY()), next)) {
                RoomUserRotation oldDirection = this.currentDirection;

                if (!this.isDrag) {
                    this.currentDirection = this.ball.getBounceDirection(this.room, this.currentDirection);
                }

                if (this.currentDirection != oldDirection) {
                    this.ball.onBounce(this.room, oldDirection, this.currentDirection, this.kicker);
                } else {
                    this.currentStep = this.totalSteps; // End the move sequence, the ball can't bounce anywhere
                }
                this.run();
            } else {
                // Move the ball & run again
                this.currentStep++;

                int delay = this.ball.getNextRollDelay(
                        this.currentStep, this.totalSteps); // Algorithm to work out the delay till next run

                if (this.ball.canStillMove(
                        this.room,
                        this.room.getLayout().getTile(this.ball.getX(), this.ball.getY()),
                        next,
                        this.currentDirection,
                        this.kicker,
                        delay,
                        this.currentStep,
                        this.totalSteps)) {
                    this.ball.onMove(
                            this.room,
                            this.room.getLayout().getTile(this.ball.getX(), this.ball.getY()),
                            next,
                            this.currentDirection,
                            this.kicker,
                            delay,
                            this.currentStep,
                            this.totalSteps);

                    this.room.sendComposer(new FloorItemOnRollerComposer(
                                    this.ball, null, next, next.getStackHeight() - this.ball.getZ(), this.room)
                            .compose());

                    Emulator.getThreading().run(this, (long) delay);
                } else {
                    this.currentStep = this.totalSteps; // End the move sequence, the ball can't bounce anywhere
                    this.run();
                }
            }
        } else {
            // We're done with the move sequence. Stop it the sequence & end the thread.
            this.ball.onStop(this.room, this.kicker, this.currentStep, this.totalSteps);
            this.dead = true;
        }
    }
}
