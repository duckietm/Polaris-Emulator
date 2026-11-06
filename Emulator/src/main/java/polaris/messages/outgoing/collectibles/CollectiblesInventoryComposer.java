package polaris.messages.outgoing.collectibles;

import java.util.List;
import java.util.Map;
import polaris.hotel.collectibles.CollectibleData.Owned;
import polaris.hotel.collectibles.CollectibleData.Product;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/** AIR 15 collectibles inventory (2539): every token, its asset id (long) and product info. */
public class CollectiblesInventoryComposer extends MessageComposer {
    private final List<Owned> owned;
    private final Map<String, Product> products;

    public CollectiblesInventoryComposer(List<Owned> owned, Map<String, Product> products) {
        this.owned = owned.stream()
                .filter(token -> products.containsKey(token.productCode()))
                .toList();
        this.products = Map.copyOf(products);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CollectiblesInventoryComposer);
        this.response.appendInt(this.owned.size());
        for (Owned token : this.owned) {
            CollectibleWire.appendLong(this.response, token.assetId());
            CollectibleWire.appendProduct(this.response, this.products.get(token.productCode()));
        }
        return this.response;
    }
}
