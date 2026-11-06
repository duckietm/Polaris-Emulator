package polaris.networking.gameserver.decoders;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.util.function.LongSupplier;
import polaris.messages.ClientMessage;
import polaris.messages.ClientMessageDispatchTiming;
import polaris.monitoring.PacketDispatchLatencyMetrics;

public final class PacketDispatchLatencyHandler extends ChannelInboundHandlerAdapter {

    private final LongSupplier nanoClock;

    public PacketDispatchLatencyHandler() {
        this(System::nanoTime);
    }

    PacketDispatchLatencyHandler(LongSupplier nanoClock) {
        this.nanoClock = nanoClock;
    }

    @Override
    public void channelRead(ChannelHandlerContext context, Object message) {
        if (message instanceof ClientMessage clientMessage) {
            long enqueuedAt = ClientMessageDispatchTiming.takeEnqueuedAt(clientMessage);
            if (enqueuedAt != 0L) {
                PacketDispatchLatencyMetrics.record(this.nanoClock.getAsLong() - enqueuedAt);
            }
        }

        context.fireChannelRead(message);
    }
}
