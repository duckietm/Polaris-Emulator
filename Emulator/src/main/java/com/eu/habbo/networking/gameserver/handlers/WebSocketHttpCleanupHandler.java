package com.eu.habbo.networking.gameserver.handlers;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;

/**
 * Removes the HTTP-only request handlers once a channel has upgraded to
 * WebSocket. They sit ahead of the WebSocket decoders and several are bound to
 * the blocking HTTP executor, so leaving them in place hops every inbound game
 * frame onto that executor for a no-op pass-through and lets a slow HTTP query
 * on a shared worker delay game traffic.
 */
public class WebSocketHttpCleanupHandler extends ChannelInboundHandlerAdapter {
    private static final String[] HTTP_ONLY_HANDLERS = {
        "nitroSecureAssetHandler",
        "nitroSecureApiHandler",
        "authHttpHandler",
        "badgeHttpHandler",
        "badgeLeaderboardHttpHandler",
        "emuStatsHttpHandler",
    };

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            ChannelPipeline pipeline = ctx.pipeline();
            for (String name : HTTP_ONLY_HANDLERS) {
                if (pipeline.get(name) != null) {
                    pipeline.remove(name);
                }
            }
            ctx.fireUserEventTriggered(evt);
            pipeline.remove(this);
            return;
        }
        super.userEventTriggered(ctx, evt);
    }
}
