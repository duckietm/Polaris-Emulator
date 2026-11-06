package polaris.networking.gameserver.decoders;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.util.function.LongSupplier;
import polaris.messages.ClientMessage;
import polaris.messages.ClientMessageDispatchTiming;

public final class PacketDispatchMarker extends ChannelInboundHandlerAdapter {

    private final LongSupplier nanoClock;

    public PacketDispatchMarker() {
        this(System::nanoTime);
    }

    PacketDispatchMarker(LongSupplier nanoClock) {
        this.nanoClock = nanoClock;
    }

    @Override
    public void channelRead(ChannelHandlerContext context, Object message) {
        if (message instanceof ClientMessage clientMessage) {
            ClientMessageDispatchTiming.markEnqueued(clientMessage, this.nanoClock.getAsLong());
        }

        context.fireChannelRead(message);
    }
}
