package polaris.networking.gameserver.decoders;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.util.List;
import polaris.messages.ClientMessage;

public class GameByteDecoder extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        short header = in.readShort();
        ByteBuf body = in.readBytes(in.readableBytes());
        out.add(new ClientMessage(header, body));
    }
}
