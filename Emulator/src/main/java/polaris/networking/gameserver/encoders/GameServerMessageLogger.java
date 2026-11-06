package polaris.networking.gameserver.encoders;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.messages.PacketNames;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.SecretBearingComposer;
import polaris.util.ANSI;

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
