package com.eu.habbo.networking.gameserver.encoders;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.PacketNames;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.SecretBearingComposer;
import com.eu.habbo.util.ANSI;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GameServerMessageLogger extends MessageToMessageEncoder<ServerMessage> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GameServerMessageLogger.class);
    private final PacketNames names;

    public GameServerMessageLogger() {
        this.names = Emulator.getGameServer().getPacketManager().getNames();
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ServerMessage message, List<Object> out) {
        LOGGER.debug(String.format(
                "[" + ANSI.BLUE + "SERVER" + ANSI.DEFAULT + "][%-4d][%-41s] => %s",
                message.getHeader(),
                this.names.getOutgoingName(message.getHeader()),
                message.getComposer() instanceof SecretBearingComposer secret && secret.carriesSecret()
                        ? "<redacted>"
                        : message.getBodyString()));

        out.add(message);
    }
}
