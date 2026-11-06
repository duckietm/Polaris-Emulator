package polaris.messages.outgoing.users;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class EmailStatusComposer extends MessageComposer {
    private final String email;
    private final boolean unknownB1;
    private final boolean unknownB2;

    public EmailStatusComposer(String email, boolean unknownB1, boolean unknownB2) {
        this.email = email;
        this.unknownB1 = unknownB1;
        this.unknownB2 = unknownB2;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.EmailStatusComposer);
        this.response.appendString(this.email);
        this.response.appendBoolean(this.unknownB1);
        this.response.appendBoolean(this.unknownB2);
        return this.response;
    }

    public String getEmail() {
        return email;
    }

    public boolean isUnknownB1() {
        return unknownB1;
    }

    public boolean isUnknownB2() {
        return unknownB2;
    }
}
