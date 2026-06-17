package com.eu.habbo.networking.gameserver.decoders;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.networking.gameserver.GameServerAttributes;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class GameMessageRateLimit extends MessageToMessageDecoder<ClientMessage> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GameMessageRateLimit.class);

    private static final int RESET_TIME = 1;
    private static final int MAX_COUNTER = 10;
    private static final int DEFAULT_GLOBAL_MAX = 50;

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
            return;
        }

        int globalMax = Emulator.getConfig().getInt("packet.global.rate.limit", DEFAULT_GLOBAL_MAX);
        if (globalCount > globalMax) {
            if (globalCount == globalMax + 1) {
                String username = (client.getHabbo() != null && client.getHabbo().getHabboInfo() != null)
                        ? client.getHabbo().getHabboInfo().getUsername() : "unauthenticated";
                LOGGER.warn("Global packet rate limit exceeded for {} ({} packets/sec) — dropping excess packets",
                        username, globalCount);
            }
            message.release();
            return;
        }

        client.incomingPacketCounter.put(message.getMessageId(), ++count);

        out.add(message);
    }

}
