package com.eu.habbo.networking.gameserver.handlers;

import com.eu.habbo.networking.gameserver.auth.AuthHttpUtil;
import com.eu.habbo.networking.gameserver.auth.ClientAddressBucket;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caps the connections one client holds before upgrading to WebSocket. The per-IP limit applied only after the
 * upgrade, so one address could open any number of HTTP connections, each able to hold its aggregation buffer. A
 * trusted proxy (loopback, Cloudflare, {@code ws.ip.header.trusted}) is not counted: every client behind it shares its
 * address. The handler is removed at the upgrade, which gives the place back.
 */
public class PreUpgradeConnectionLimit extends ChannelInboundHandlerAdapter {
    private static final Map<String, Integer> OPEN = new ConcurrentHashMap<>();

    private final int maxPerClient;
    private String countedBucket;

    public PreUpgradeConnectionLimit(int maxPerClient) {
        this.maxPerClient = maxPerClient;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        if (this.maxPerClient > 0
                && ctx.channel().remoteAddress() instanceof InetSocketAddress address
                && address.getAddress() != null
                && !AuthHttpUtil.isTrustedProxy(ctx)) {
            String bucket = ClientAddressBucket.of(address.getAddress().getHostAddress());
            int open = OPEN.merge(bucket, 1, Integer::sum);
            this.countedBucket = bucket;
            if (open > this.maxPerClient) {
                ctx.close();
                return;
            }
        }
        super.channelActive(ctx);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        this.release();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        this.release();
        super.channelInactive(ctx);
    }

    private void release() {
        String bucket = this.countedBucket;
        if (bucket == null) return;
        this.countedBucket = null;
        OPEN.computeIfPresent(bucket, (key, open) -> open > 1 ? open - 1 : null);
    }

    /** Visible for tests. */
    static int openFor(String ip) {
        return OPEN.getOrDefault(ClientAddressBucket.of(ip), 0);
    }
}
