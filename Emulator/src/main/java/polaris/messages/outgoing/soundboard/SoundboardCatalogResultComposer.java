package polaris.messages.outgoing.soundboard;

import polaris.hotel.soundboard.SoundboardCatalogResult;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

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
