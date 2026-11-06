package polaris.networking.gameserver.decoders;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.messages.ClientMessage;
import polaris.messages.PacketNames;
import polaris.util.ANSI;

public class GameClientMessageLogger extends MessageToMessageDecoder<ClientMessage> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GameClientMessageLogger.class);
    private final PacketNames names;

    public GameClientMessageLogger() {
        this.names = Emulator.getGameServer().getPacketManager().getNames();
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ClientMessage message, List<Object> out) {
        LOGGER.debug(String.format(
                "[" + ANSI.GREEN + "CLIENT" + ANSI.DEFAULT + "][%-4d][%-41s] => %s",
                message.getMessageId(),
                this.names.getIncomingName(message.getMessageId()),
                message.getMessageBody()));

        out.add(message);
    }
}
