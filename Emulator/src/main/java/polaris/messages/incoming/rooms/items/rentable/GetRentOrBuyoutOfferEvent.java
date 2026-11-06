package polaris.messages.incoming.rooms.items.rentable;

import polaris.Emulator;
import polaris.hotel.catalog.CatalogItem;
import polaris.hotel.items.Item;
import polaris.hotel.items.rentable.RentableFurnitureManager;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.items.rentablespaces.FurniRentOrBuyoutOfferComposer;

/**
 * GetRentOrBuyoutOffer (2518): the rent confirmation dialog asks the price of
 * extending ({@code buyout = false}, rent offer) or buying out
 * ({@code buyout = true}, purchase offer) a furni type.
 */
public class GetRentOrBuyoutOfferEvent extends MessageHandler {
    private static final int MAX_FURNI_NAME_LENGTH = 128;

    @Override
    public void handle() throws Exception {
        boolean isWallItem = this.packet.readBoolean();
        String furniTypeName = this.packet.readString();
        boolean buyout = this.packet.readBoolean();

        if (furniTypeName == null || furniTypeName.isEmpty() || furniTypeName.length() > MAX_FURNI_NAME_LENGTH) return;

        Item baseItem = Emulator.getGameEnvironment().getItemManager().getItem(furniTypeName);

        if (baseItem == null) return;

        CatalogItem offer = RentableFurnitureManager.findOffer(baseItem, !buyout);

        if (offer == null) return;

        this.client.sendResponse(new FurniRentOrBuyoutOfferComposer(
                isWallItem, furniTypeName, buyout, offer.getCredits(), offer.getPoints(), offer.getPointsType()));
    }
}
