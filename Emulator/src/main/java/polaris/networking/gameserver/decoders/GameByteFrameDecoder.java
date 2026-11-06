package polaris.networking.gameserver.decoders;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

public class GameByteFrameDecoder extends LengthFieldBasedFrameDecoder {

    /**
     * MAX_PACKET_LENGTH is based on the maximum camera PNG size.
     * Source: https://superuser.com/a/759030
     * Maximum camera packet is 320 * 320 Pixel * 4 Bytes per Pixel = 409600.
     * Adding some for overhead 409600 + 8192 = 417792
     */
    private static final int MAX_PACKET_LENGTH = 417792;

    private static final int LENGTH_FIELD_OFFSET = 0;
    private static final int LENGTH_FIELD_LENGTH = 4;
    private static final int LENGTH_FIELD_ADJUSTMENT = 0;
    private static final int INITIAL_BYTES_TO_STRIP = 4;

    public GameByteFrameDecoder() {
        super(
                MAX_PACKET_LENGTH,
                LENGTH_FIELD_OFFSET,
                LENGTH_FIELD_LENGTH,
                LENGTH_FIELD_ADJUSTMENT,
                INITIAL_BYTES_TO_STRIP);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        return super.decode(ctx, in);
    }
}
