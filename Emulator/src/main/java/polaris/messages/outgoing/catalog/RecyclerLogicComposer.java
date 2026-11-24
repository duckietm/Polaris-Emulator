package polaris.messages.outgoing.catalog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import polaris.Emulator;
import polaris.hotel.catalog.CatalogManager;
import polaris.hotel.catalog.RecyclerPrize;
import polaris.hotel.items.Item;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RecyclerLogicComposer extends MessageComposer {
    /** One line of the prize list: a furni by its name, type code and sprite id, or a chat style by its id. */
    private record Entry(String name, String type, int id) {}

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RecyclerLogicComposer);
        CatalogManager catalog = Emulator.getGameEnvironment().getCatalogManager();
        Map<Integer, Set<Item>> prizes = catalog.getRecyclerPrizesSnapshot();
        Map<Integer, Set<Integer>> chatStyles = catalog.getRecyclerChatStylePrizesSnapshot();
        Set<Integer> levelIds = new TreeSet<>(prizes.keySet());
        levelIds.addAll(chatStyles.keySet());

        this.response.appendInt(levelIds.size());
        for (int level : levelIds) {
            this.response.appendInt(level);
            this.response.appendInt(Emulator.getConfig().getInt("hotel.ecotron.rarity.chance." + level, 0));

            List<Entry> entries = entries(prizes.get(level), chatStyles.get(level));
            this.response.appendInt(entries.size());
            for (Entry entry : entries) {
                this.response.appendString(entry.name());
                this.response.appendInt(1);
                this.response.appendString(entry.type());
                this.response.appendInt(entry.id());
            }
        }
        return this.response;
    }

    /** The furni of a level by id, then its chat styles by id (the client's prize type "chat_style"). */
    private static List<Entry> entries(Set<Item> items, Set<Integer> chatStyles) {
        List<Entry> entries = new ArrayList<>();

        if (items != null) {
            List<Item> sorted = new ArrayList<>(items);
            sorted.sort(Comparator.comparingInt(Item::getId));
            for (Item item : sorted) {
                entries.add(new Entry(item.getName(), item.getType().code.toLowerCase(), item.getSpriteId()));
            }
        }

        if (chatStyles != null) {
            for (int style : new TreeSet<>(chatStyles)) {
                entries.add(new Entry("", RecyclerPrize.CHAT_STYLE_TYPE, style));
            }
        }

        return entries;
    }
}
