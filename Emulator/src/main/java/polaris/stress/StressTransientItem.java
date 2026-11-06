package polaris.stress;

import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionDefault;

final class StressTransientItem extends InteractionDefault {
    StressTransientItem(int id, int userId, Item item) {
        super(id, userId, item, "0", 0, 0);
        // Temporary like a Place Furni copy: picked up, it is removed, never handed to the room owner's inventory
        // where it could be traded or recycled.
        this.markTemporary();
    }

    @Override
    public void run() {
        // Stress entities are deliberately never persisted.
    }
}
