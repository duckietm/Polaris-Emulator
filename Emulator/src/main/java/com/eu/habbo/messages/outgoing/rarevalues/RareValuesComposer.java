package com.eu.habbo.messages.outgoing.rarevalues;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.map.TIntObjectMap;

// Sends the full spriteId -> value map to the client. Consumed by the toolbar
// price guide and the furni infostand "value" line. See CatalogManager#loadFurnitureValues.
public class RareValuesComposer extends MessageComposer {
    private final TIntObjectMap<int[]> values;

    public RareValuesComposer(TIntObjectMap<int[]> values) {
        this.values = values;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RareValuesComposer);
        this.response.appendInt(this.values.size());

        TIntObjectIterator<int[]> iterator = this.values.iterator();
        while (iterator.hasNext()) {
            iterator.advance();
            int[] value = iterator.value();
            this.response.appendInt(iterator.key()); // spriteId
            this.response.appendInt(value[0]);        // credits
            this.response.appendInt(value[1]);        // points
            this.response.appendInt(value[2]);        // pointsType
        }

        return this.response;
    }
}
