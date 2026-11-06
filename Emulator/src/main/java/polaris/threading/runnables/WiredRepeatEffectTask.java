package polaris.threading.runnables;

import polaris.Emulator;
import polaris.hotel.items.interactions.InteractionWiredEffect;
import polaris.hotel.rooms.Room;

class WiredRepeatEffectTask implements Runnable {
    private final InteractionWiredEffect effect;
    private final Room room;
    private final int delay;

    public WiredRepeatEffectTask(InteractionWiredEffect effect, Room room, int delay) {
        this.effect = effect;
        this.room = room;
        this.delay = delay;
    }

    @Override
    public void run() {
        if (!Emulator.isShuttingDown && Emulator.isReady) {
            if (this.effect == null) return;

            if (this.room != null && this.room.isLoaded() && this.room.getId() == this.effect.getRoomId()) {
                try {
                    this.effect.execute(null, this.room, null);
                    Emulator.getThreading().run(this, this.delay);
                } catch (Exception e) {
                    // Prevent task from crashing the thread pool
                }
            }
        }
    }
}
