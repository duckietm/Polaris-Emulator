package polaris.stress;

import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerGameStarts;

final class StressWiredTrigger extends WiredTriggerGameStarts {
    StressWiredTrigger(int id, int userId, Item item) {
        super(id, userId, item, "0", 0, 0);
    }

    @Override
    public void run() {
        // Stress entities are deliberately never persisted.
    }
}
