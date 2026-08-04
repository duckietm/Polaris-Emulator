package com.eu.habbo.messages.outgoing.soundboard;

import com.eu.habbo.habbohotel.soundboard.SoundboardCatalogResult;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class SoundboardCatalogResultComposer extends MessageComposer {
    private final Operation operation;
    private final SoundboardCatalogResult result;

    public SoundboardCatalogResultComposer(Operation operation, SoundboardCatalogResult result) {
        this.operation = operation;
        this.result = result;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.SoundboardCatalogResultComposer);
        this.response.appendInt(this.operation.wireCode());
        this.response.appendInt(this.result.code().wireCode());
        this.response.appendInt(this.result.soundId());
        return this.response;
    }

    public enum Operation {
        UPSERT(1),
        REORDER(2);

        private final int wireCode;

        Operation(int wireCode) {
            this.wireCode = wireCode;
        }

        public int wireCode() {
            return this.wireCode;
        }
    }
}
