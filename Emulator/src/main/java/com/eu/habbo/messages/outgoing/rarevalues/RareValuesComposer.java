package com.eu.habbo.messages.outgoing.rarevalues;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.map.TIntObjectMap;

public class RareValuesComposer extends MessageComposer {
    private final TIntObjectMap<int[]> values;
    private final byte[] snapshot;

    public RareValuesComposer(byte[] snapshot) {
        this.values = null;
        this.snapshot = snapshot;
    }

    public RareValuesComposer(TIntObjectMap<int[]> values) {
        this.values = values;
        this.snapshot = null;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RareValuesComposer);

        if (this.snapshot != null) {
            this.response.appendRawBytes(this.snapshot);
            return this.response;
        }

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
