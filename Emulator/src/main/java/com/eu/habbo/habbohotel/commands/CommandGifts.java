package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemManager;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gifts sent by the gift commands. The content was created first and the wrapper picked afterwards: with no gift
 * wrapper configured nextInt(0) threw, with a wrapper id that is no furni createGift failed, and in both cases the
 * content stayed behind as an orphan row; inside :massgift the exception also ended the loop, in silence.
 */
final class CommandGifts {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommandGifts.class);

    private CommandGifts() {}

    /** A gift wrapper that exists, picked at random, or null when the hotel has none. */
    static Item pickWrapper(Map<Integer, Integer> giftFurniture, ItemManager items, Random random) {
        if (giftFurniture == null || giftFurniture.isEmpty()) return null;

        List<Integer> ids = new ArrayList<>(giftFurniture.values());
        int start = random.nextInt(ids.size());

        for (int i = 0; i < ids.size(); i++) {
            Integer id = ids.get((start + i) % ids.size());
            Item wrapper = id == null ? null : items.getItem(id);

            if (wrapper != null) return wrapper;
        }

        return null;
    }

    /**
     * Creates the content and the gift that holds it; when the gift cannot be made the content is removed again, so
     * no content is left behind. True when the gift was delivered.
     */
    static boolean send(ItemManager items, Item content, Item wrapper, String username, String message) {
        if (content == null || wrapper == null) return false;

        HabboItem item = null;
        try {
            item = items.createGiftContent(ItemManager.GIFT_CONTENT_OWNER, content, 0, 0, "");

            if (item == null) return false;

            String extraData = "1\t" + item.getId() + "\t0\t0\t0\t" + message + "\t0\t0";

            if (items.createGift(username, wrapper, extraData, 0, 0) != null) return true;
        } catch (RuntimeException e) {
            LOGGER.error("Could not send a command gift to {}", username, e);
        }

        if (item != null) items.deleteItem(item);

        return false;
    }
}
