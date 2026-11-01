package com.eu.habbo.networking.gameserver.decoders;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.networking.gameserver.GameServerAttributes;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GameMessageRateLimit extends MessageToMessageDecoder<ClientMessage> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GameMessageRateLimit.class);

    private static final int RESET_TIME = 1;
    private static final int MAX_COUNTER = 10;
    private static final int DEFAULT_GLOBAL_MAX = 50;
    // A client that keeps sending past the limits was only ever dropped, so it could flood the decoder for as long
    // as it liked. More drops than this within one second close the connection; 0 never closes it.
    private static final int DEFAULT_DISCONNECT_AFTER = 200;

    // One limiter per channel, so the drop count belongs to that connection.
    private int droppedInWindow;
    private int dropWindowStart;

    @Override
    protected void decode(ChannelHandlerContext ctx, ClientMessage message, List<Object> out) throws Exception {
        GameClient client = ctx.channel().attr(GameServerAttributes.CLIENT).get();

        // ClientMessage is not ReferenceCounted, so MessageToMessageDecoder's
        // auto-release is a no-op for it; on every drop path we must release the
        // wrapped ByteBuf ourselves or it leaks (it is only released downstream
        // in ChannelReadHandler on the success path).
        if (client == null) {
            message.release();
            return;
        }

        int count = 0;
        int globalCount = 0;

        ConfigurationManager config = Emulator.getConfig();
        int timestamp = Emulator.getIntUnixTimestamp();
        if (timestamp - client.lastPacketCounterCleared > RESET_TIME) {
            client.incomingPacketCounter.clear();
            client.lastPacketCounterCleared = timestamp;
        } else {
            count = client.incomingPacketCounter.getOrDefault(message.getMessageId(), 0);
            for (int c : client.incomingPacketCounter.values()) {
                globalCount += c;
            }
        }

        if (count > MAX_COUNTER) {
            message.release();
            this.dropped(ctx, client, config, timestamp);
            return;
        }

        int globalMax = config.getInt("packet.global.rate.limit", DEFAULT_GLOBAL_MAX);
        if (globalCount > globalMax) {
            if (globalCount == globalMax + 1) {
                String username =
                        (client.getHabbo() != null && client.getHabbo().getHabboInfo() != null)
                                ? client.getHabbo().getHabboInfo().getUsername()
                                : "unauthenticated";
                LOGGER.warn(
                        "Global packet rate limit exceeded for {} ({} packets/sec) — dropping excess packets",
                        username,
                        globalCount);
            }
            message.release();
            this.dropped(ctx, client, config, timestamp);
            return;
        }

        client.incomingPacketCounter.put(message.getMessageId(), ++count);

        out.add(message);
    }

    private void dropped(ChannelHandlerContext ctx, GameClient client, ConfigurationManager config, int timestamp) {
        if (timestamp != this.dropWindowStart) {
            this.dropWindowStart = timestamp;
            this.droppedInWindow = 0;
        }
        this.droppedInWindow++;

        int disconnectAfter = config.getInt("packet.rate.limit.disconnect_after", DEFAULT_DISCONNECT_AFTER);
        if (disconnectAfter > 0 && this.droppedInWindow == disconnectAfter + 1) {
            String username = (client.getHabbo() != null && client.getHabbo().getHabboInfo() != null)
                    ? client.getHabbo().getHabboInfo().getUsername()
                    : "unauthenticated";
            LOGGER.warn(
                    "Closing the connection of {}: {} packets dropped by the rate limit within one second",
                    username,
                    this.droppedInWindow);
            ctx.channel().close();
        }
    }
}
