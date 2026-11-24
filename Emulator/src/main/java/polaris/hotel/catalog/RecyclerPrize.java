package polaris.hotel.catalog;

import java.util.List;
import java.util.function.IntUnaryOperator;
import polaris.hotel.items.Item;

/**
 * What a Furni-Matic exchange can pay: a furni, or a chat style (the official client's prize type "chat_style").
 *
 * <p>A box keeps its prize as one number ({@code items_presents.base_item_reward}): the furni definition id, or the
 * chat style id negated. Boxes made before chat styles could be won hold a positive number and open as they did.
 */
public record RecyclerPrize(Item item, int chatStyleId) {
    /** The prize type the client's prize list names a chat style with. */
    public static final String CHAT_STYLE_TYPE = "chat_style";

    public static RecyclerPrize furni(Item item) {
        return new RecyclerPrize(item, 0);
    }

    public static RecyclerPrize chatStyle(int styleId) {
        return new RecyclerPrize(null, styleId);
    }

    public boolean isChatStyle() {
        return this.item == null && this.chatStyleId > 0;
    }

    /** The number a box holds for this prize; 0 for a prize that is neither. */
    public int boxReward() {
        if (this.item != null) return this.item.getId();

        return this.chatStyleId > 0 ? -this.chatStyleId : 0;
    }

    /** The chat style a box holds, or 0 when its number names a furni or nothing. */
    public static int chatStyleOf(int boxReward) {
        return boxReward < 0 ? -boxReward : 0;
    }

    /** One of the candidates, each as likely as the next; null when there are none. */
    public static RecyclerPrize pick(List<RecyclerPrize> candidates, IntUnaryOperator nextInt) {
        if (candidates == null || candidates.isEmpty()) return null;

        return candidates.get(nextInt.applyAsInt(candidates.size()));
    }
}
