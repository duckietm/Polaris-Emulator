package polaris.messages.outgoing.catalog;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import polaris.Emulator;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class GiftConfigurationComposer extends MessageComposer {
    public static volatile List<Integer> BOX_TYPES = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 8);
    public static volatile List<Integer> RIBBON_TYPES = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.GiftConfigurationComposer);
        this.response.appendBoolean(true);
        this.response.appendInt(Emulator.getConfig().getInt("hotel.gifts.special.price", 2));

        var giftWrapping = Emulator.getGameEnvironment().getCatalogManager().getGiftWrappingSnapshot();

        this.response.appendInt(giftWrapping.wrappers().size());
        for (Integer i : giftWrapping.wrappers().keySet()) {
            this.response.appendInt(i);
        }

        this.response.appendInt(BOX_TYPES.size());
        for (Integer type : BOX_TYPES) {
            this.response.appendInt(type);
        }

        this.response.appendInt(RIBBON_TYPES.size());
        for (Integer type : RIBBON_TYPES) {
            this.response.appendInt(type);
        }

        this.response.appendInt(giftWrapping.furniture().size());

        for (Map.Entry<Integer, Integer> set : giftWrapping.furniture().entrySet()) {
            this.response.appendInt(set.getKey());
        }

        return this.response;
    }
}
