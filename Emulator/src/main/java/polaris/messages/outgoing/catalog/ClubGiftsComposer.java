package polaris.messages.outgoing.catalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import polaris.Emulator;
import polaris.hotel.catalog.CatalogItem;
import polaris.hotel.catalog.CatalogPage;
import polaris.hotel.catalog.CatalogPageLayouts;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class ClubGiftsComposer extends MessageComposer {

    private final int daysTillNextGift;
    private final int availableGifts;
    private final int daysAsHc;

    public ClubGiftsComposer(int daysTillNextGift, int availableGifts, int daysAsHc) {
        this.daysTillNextGift = daysTillNextGift;
        this.availableGifts = availableGifts;
        this.daysAsHc = daysAsHc;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ClubGiftsComposer);

        this.response.appendInt(this.daysTillNextGift); // Days Until Next Gift
        this.response.appendInt(this.availableGifts); // Gift Selectable

        CatalogPage page = Emulator.getGameEnvironment()
                .getCatalogManager()
                .getCatalogPageByLayout(CatalogPageLayouts.club_gift.name().toLowerCase());

        if (page != null) {
            final List<CatalogItem> items =
                    new ArrayList<>(page.getCatalogItems().values());
            Collections.sort(items);

            this.response.appendInt(items.size());
            for (CatalogItem item : items) {
                item.serialize(this.response);
            }

            this.response.appendInt(items.size());
            for (CatalogItem item : items) {
                int daysRequired = 0;
                try {
                    daysRequired = Integer.parseInt(item.getExtradata());
                } catch (NumberFormatException ignored) {
                }

                this.response.appendInt(item.getId());
                this.response.appendBoolean(item.isClubOnly());
                this.response.appendInt(daysRequired);
                this.response.appendBoolean(daysRequired <= daysAsHc);
            }
        } else {
            this.response.appendInt(0);
            this.response.appendInt(0);
        }

        return this.response;
    }

    public int getDaysTillNextGift() {
        return daysTillNextGift;
    }

    public int getAvailableGifts() {
        return availableGifts;
    }

    public int getDaysAsHc() {
        return daysAsHc;
    }
}
