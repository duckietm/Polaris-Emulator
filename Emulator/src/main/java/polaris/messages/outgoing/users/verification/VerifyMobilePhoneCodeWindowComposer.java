package polaris.messages.outgoing.users.verification;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class VerifyMobilePhoneCodeWindowComposer extends MessageComposer {
    private final int unknownInt1;
    private final int unknownInt2;

    public VerifyMobilePhoneCodeWindowComposer(int unknownInt1, int unknownInt2) {
        this.unknownInt1 = unknownInt1;
        this.unknownInt2 = unknownInt2;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.VerifyMobilePhoneCodeWindowComposer);
        this.response.appendInt(this.unknownInt1);
        this.response.appendInt(this.unknownInt2);
        return this.response;
    }

    public int getUnknownInt1() {
        return unknownInt1;
    }

    public int getUnknownInt2() {
        return unknownInt2;
    }
}
