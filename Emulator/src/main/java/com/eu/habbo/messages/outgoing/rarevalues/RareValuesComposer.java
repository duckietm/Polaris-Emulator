package com.eu.habbo.messages.outgoing.rarevalues;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

public class RareValuesComposer extends MessageComposer {
    private final Int2ObjectMap<int[]> values;
    private final byte[] snapshot;

    public RareValuesComposer(byte[] snapshot) {
        this.values = null;
        this.snapshot = snapshot;
    }

    public RareValuesComposer(Int2ObjectMap<int[]> values) {
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

        for (Int2ObjectMap.Entry<int[]> entry : this.values.int2ObjectEntrySet()) {
            int[] value = entry.getValue();
            this.response.appendInt(entry.getIntKey()); // spriteId
            this.response.appendInt(value[0]);        // credits
            this.response.appendInt(value[1]);        // points
            this.response.appendInt(value[2]);        // pointsType
        }

        return this.response;
    }
}
