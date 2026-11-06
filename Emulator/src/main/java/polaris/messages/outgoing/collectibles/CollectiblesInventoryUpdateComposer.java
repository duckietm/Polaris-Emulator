package polaris.messages.outgoing.collectibles;

import java.util.List;
import java.util.Map;
import polaris.hotel.collectibles.CollectibleData.Owned;
import polaris.hotel.collectibles.CollectibleData.Product;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/** AIR 15 collectibles inventory change (1475): the tokens added, then the tokens removed. */
public class CollectiblesInventoryUpdateComposer extends MessageComposer {
    private final List<Owned> added;
    private final List<Owned> removed;
    private final Map<String, Product> products;

    public CollectiblesInventoryUpdateComposer(List<Owned> added, List<Owned> removed, Map<String, Product> products) {
        this.added = added.stream()
                .filter(token -> products.containsKey(token.productCode()))
                .toList();
        this.removed = removed.stream()
                .filter(token -> products.containsKey(token.productCode()))
                .toList();
        this.products = Map.copyOf(products);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CollectiblesInventoryUpdateComposer);
        this.response.appendInt(this.added.size());
        for (Owned token : this.added) {
            CollectibleWire.appendLong(this.response, token.assetId());
            CollectibleWire.appendProduct(this.response, this.products.get(token.productCode()));
        }
        this.response.appendInt(this.removed.size());
        for (Owned token : this.removed) {
            CollectibleWire.appendLong(this.response, token.assetId());
            CollectibleWire.appendProduct(this.response, this.products.get(token.productCode()));
        }
        return this.response;
    }
}
