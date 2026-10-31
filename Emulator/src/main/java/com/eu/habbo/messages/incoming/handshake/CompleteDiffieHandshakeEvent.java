package com.eu.habbo.messages.incoming.handshake;

import com.eu.habbo.Emulator;
import com.eu.habbo.crypto.HabboRC4;
import com.eu.habbo.messages.NoAuthMessage;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.handshake.CompleteDiffieHandshakeComposer;
import com.eu.habbo.networking.gameserver.decoders.GameByteDecryption;
import com.eu.habbo.networking.gameserver.encoders.GameByteEncryption;
import com.eu.habbo.networking.gameserver.GameServerAttributes;

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
        this.client.sendResponse(new CompleteDiffieHandshakeComposer(this.client.getEncryption().getDiffie().getPublicKey()));

        this.client.getChannel().attr(GameServerAttributes.CRYPTO_CLIENT).set(new HabboRC4(sharedKey));
        this.client.getChannel().attr(GameServerAttributes.CRYPTO_SERVER).set(new HabboRC4(sharedKey));

        this.client.getChannel().pipeline().addFirst(new GameByteDecryption());
        this.client.getChannel().pipeline().addFirst(new GameByteEncryption());
    }

}
