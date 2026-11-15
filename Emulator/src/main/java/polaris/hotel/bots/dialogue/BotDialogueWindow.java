package polaris.hotel.bots.dialogue;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A bot dialogue shown in a window of its own instead of the bot's menu: a shop counter, a bank desk, a notice board.
 * The window has a title, what the bot says, up to {@link #MAX_TABS} tabs, a grid of items with a furni icon, a price
 * and a line of detail, a few footer lines (a balance, say) and the label of the button that takes the selected item.
 *
 * <p>Everything is cut to size here, as in {@link BotDialoguePage}: texts lose control characters and are cut to their
 * maximum, at most {@link #MAX_ITEMS} items and {@link #MAX_FOOTER} footer lines are kept, an icon that is not a furni
 * class name is dropped, and an item's tab outside the window's tabs shows it under every tab.
 */
public record BotDialogueWindow(
        String title, String text, List<String> tabs, List<Item> items, List<String> footer, String action) {
    public static final int MAX_TITLE = 40;
    public static final int MAX_TABS = 4;
    public static final int MAX_TAB = 20;
    public static final int MAX_ITEMS = 24;
    public static final int MAX_DETAIL = 40;
    public static final int MAX_FOOTER = 3;
    public static final int MAX_AMOUNT = 99;
    public static final int MAX_ICON = 100;

    /** What a furni class name looks like; anything else is not sent as an icon. */
    private static final Pattern ICON = Pattern.compile("[A-Za-z0-9_.*-]+");

    /**
     * An item of the grid: the key the dialogue gets back, its label, the furni class name of its icon (empty for
     * none), its price (0 to hide it), a line of detail, the tab it belongs to (-1 for every tab), and how many the
     * player may take at once (1 hides the amount).
     */
    public record Item(String key, String label, String icon, long price, String detail, int tab, int maxAmount) {
        public Item {
            key = key == null ? "" : key;

            if (key.isBlank() || key.length() > BotDialoguePage.MAX_KEY) {
                throw new IllegalArgumentException("An item key is 1 to " + BotDialoguePage.MAX_KEY + " characters");
            }

            label = BotDialoguePage.clean(label, BotDialoguePage.MAX_LABEL);
            icon = icon == null
                            || icon.length() > MAX_ICON
                            || !ICON.matcher(icon).matches()
                    ? ""
                    : icon;
            price = Math.max(0, price);
            detail = BotDialoguePage.clean(detail, MAX_DETAIL);
            maxAmount = Math.clamp(maxAmount, 1, MAX_AMOUNT);
        }

        Item everywhere() {
            return new Item(this.key, this.label, this.icon, this.price, this.detail, -1, this.maxAmount);
        }
    }

    public BotDialogueWindow {
        title = BotDialoguePage.clean(title, MAX_TITLE);
        text = BotDialoguePage.clean(text, BotDialoguePage.MAX_TEXT);
        action = BotDialoguePage.clean(action, BotDialoguePage.MAX_LABEL);
        tabs = cleanAll(tabs, MAX_TABS, MAX_TAB);
        footer = cleanAll(footer, MAX_FOOTER, BotDialoguePage.MAX_LABEL);
        List<Item> kept = new ArrayList<>();

        if (items != null) {
            for (Item item : items) {
                if (item != null && kept.size() < MAX_ITEMS) {
                    kept.add(item.tab() >= tabs.size() || item.tab() < -1 ? item.everywhere() : item);
                }
            }
        }

        items = List.copyOf(kept);
    }

    /** An item with an icon, a price and a line of detail, taken one at a time. */
    public static Item item(String key, String label, String icon, long price, String detail) {
        return new Item(key, label, icon, price, detail, -1, 1);
    }

    /** The same item under the tab at {@code tab}. */
    public static Item inTab(Item item, int tab) {
        return new Item(item.key(), item.label(), item.icon(), item.price(), item.detail(), tab, item.maxAmount());
    }

    /** The same item, of which the player may take up to {@code maxAmount} at once. */
    public static Item upTo(Item item, int maxAmount) {
        return new Item(item.key(), item.label(), item.icon(), item.price(), item.detail(), item.tab(), maxAmount);
    }

    private static List<String> cleanAll(List<String> values, int count, int max) {
        List<String> kept = new ArrayList<>();

        if (values != null) {
            for (String value : values) {
                if (kept.size() < count) {
                    kept.add(BotDialoguePage.clean(value, max));
                }
            }
        }

        return List.copyOf(kept);
    }
}
