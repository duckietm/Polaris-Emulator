package polaris.messages.outgoing.catalog;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class PetNameErrorComposer extends MessageComposer {
    public static final int NAME_OK = 0;
    public static final int NAME_TO_LONG = 1;
    public static final int NAME_TO_SHORT = 2;
    public static final int FORBIDDEN_CHAR = 3;
    public static final int FORBIDDEN_WORDS = 4;

    private final int type;
    private final String value;

    public PetNameErrorComposer(int type, String value) {
        this.type = type;
        this.value = value;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.PetNameErrorComposer);
        this.response.appendInt(this.type);
        this.response.appendString(this.value);
        return this.response;
    }

    public int getType() {
        return type;
    }

    public String getValue() {
        return value;
    }
}
