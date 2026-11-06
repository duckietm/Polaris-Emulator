package polaris.messages.outgoing.handshake;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class CompleteDiffieHandshakeComposer extends MessageComposer {

    private final String publicKey;
    private final boolean clientEncryption;

    public CompleteDiffieHandshakeComposer(String publicKey) {
        this(publicKey, true);
    }

    public CompleteDiffieHandshakeComposer(String publicKey, boolean clientEncryption) {
        this.publicKey = publicKey;
        this.clientEncryption = clientEncryption;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CompleteDiffieHandshakeComposer);
        this.response.appendString(this.publicKey);
        this.response.appendBoolean(this.clientEncryption);
        return this.response;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public boolean isClientEncryption() {
        return clientEncryption;
    }
}
