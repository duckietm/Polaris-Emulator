package polaris.messages.incoming.handshake;

import polaris.Emulator;
import polaris.crypto.PolarisRC4;
import polaris.messages.NoAuthMessage;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.handshake.CompleteDiffieHandshakeComposer;
import polaris.networking.gameserver.GameServerAttributes;
import polaris.networking.gameserver.decoders.GameByteDecryption;
import polaris.networking.gameserver.encoders.GameByteEncryption;

@NoAuthMessage
public class CompleteDiffieHandshakeEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        if (this.client.getEncryption() == null) {
            Emulator.getGameServer().getGameClientManager().disposeClient(this.client);
            return;
        }

        // A second handshake on the same connection would swap the stream keys again.
        if (this.client.isHandshakeFinished()) {
            return;
        }

        byte[] sharedKey = this.client.getEncryption().getDiffie().getSharedKey(this.packet.readString());

        this.client.setHandshakeFinished(true);
        this.client.sendResponse(new CompleteDiffieHandshakeComposer(
                this.client.getEncryption().getDiffie().getPublicKey()));

        this.client.getChannel().attr(GameServerAttributes.CRYPTO_CLIENT).set(new PolarisRC4(sharedKey));
        this.client.getChannel().attr(GameServerAttributes.CRYPTO_SERVER).set(new PolarisRC4(sharedKey));

        this.client.getChannel().pipeline().addFirst(new GameByteDecryption());
        this.client.getChannel().pipeline().addFirst(new GameByteEncryption());
    }
}
