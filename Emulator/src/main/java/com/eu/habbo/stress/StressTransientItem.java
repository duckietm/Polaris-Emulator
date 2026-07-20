package com.eu.habbo.stress;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionDefault;

final class StressTransientItem extends InteractionDefault {
    StressTransientItem(int id, int userId, Item item) {
        super(id, userId, item, "0", 0, 0);
    }

    @Override
    public void run() {
        // Stress entities are deliberately never persisted.
    }
}
