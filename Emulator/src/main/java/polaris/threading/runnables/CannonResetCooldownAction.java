package polaris.threading.runnables;

import polaris.hotel.items.interactions.InteractionCannon;

public class CannonResetCooldownAction implements Runnable {
    private final InteractionCannon cannon;

    public CannonResetCooldownAction(InteractionCannon cannon) {
        this.cannon = cannon;
    }

    @Override
    public void run() {
        if (this.cannon != null) {
            this.cannon.cooldown = false;
        }
    }
}
