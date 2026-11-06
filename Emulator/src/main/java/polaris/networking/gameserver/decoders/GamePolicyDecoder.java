package polaris.networking.gameserver.decoders;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.util.CharsetUtil;
import java.util.List;

public class GamePolicyDecoder extends ByteToMessageDecoder {

    private static final String POLICY = "<?xml version=\"1.0\"?>\n"
            + "  <!DOCTYPE cross-domain-policy SYSTEM \"/xml/dtds/cross-domain-policy.dtd\">\n"
            + "  <cross-domain-policy>\n"
            + "  <allow-access-from domain=\"*\" to-ports=\"1-31111\" />\n"
            + "  </cross-domain-policy>"
            + (char) 0;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        in.markReaderIndex();

        byte b = in.readByte();
        if (b == '<') {
            in.resetReaderIndex();
            ctx.writeAndFlush(Unpooled.copiedBuffer(POLICY, CharsetUtil.UTF_8))
                    .addListener(ChannelFutureListener.CLOSE);
            return;
        }

        // Remove ourselves since the first packet was not a policy request.
        ctx.pipeline().remove(this);

        // Continue to the other pipelines.
        in.resetReaderIndex();
    }
}
