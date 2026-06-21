package com.eu.habbo.networking.rconserver;


import com.eu.habbo.Emulator;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class RCONServerHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RCONServerHandler.class);

    // Gson is thread-safe and immutable once built — share one instance instead
    // of allocating a parser per RCON request.
    private static final Gson GSON = new Gson();
    private static final int DEFAULT_MAX_PAYLOAD_BYTES = 64 * 1024;

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        String address = remoteAddress(ctx);

        for (String s : Emulator.getRconServer().allowedAdresses) {
            if (s.equalsIgnoreCase(address)) {
                return;
            }
        }

        ctx.channel().close();

        LOGGER.warn("RCON Remote connection closed: {}. IP not allowed!", address);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof ByteBuf data)) {
            ctx.fireChannelRead(msg);
            return;
        }

        try {
            int readableBytes = data.readableBytes();
            int maxPayloadBytes = maxPayloadBytes();
            if (readableBytes > maxPayloadBytes) {
                writeAndClose(ctx, "PAYLOAD_TOO_LARGE");
                LOGGER.warn("Rejected oversized RCON payload: {} bytes (max {})", readableBytes, maxPayloadBytes);
                return;
            }

            byte[] d = new byte[readableBytes];
            data.getBytes(0, d);
            String message = new String(d, java.nio.charset.StandardCharsets.UTF_8);
            Gson gson = GSON;
            String response = "ERROR";
            String key = "";
            try {
                JsonObject object = gson.fromJson(message, JsonObject.class);
                key = object.get("key").getAsString();
                response = Emulator.getRconServer().handle(ctx, key, object.get("data").toString());
            } catch (ArrayIndexOutOfBoundsException e) {
                LOGGER.error("Unknown RCON Message: {}", key);
            } catch (Exception e) {
                LOGGER.error("Invalid RCON Message: {}", message);
                e.printStackTrace();
            }

            writeAndClose(ctx, response);
        } finally {
            data.release();
        }
    }

    static int maxPayloadBytes() {
        if (Emulator.getConfig() == null) {
            return DEFAULT_MAX_PAYLOAD_BYTES;
        }

        int configured = Emulator.getConfig().getInt("rcon.max_payload_bytes", DEFAULT_MAX_PAYLOAD_BYTES);
        return configured > 0 ? configured : DEFAULT_MAX_PAYLOAD_BYTES;
    }

    static String remoteAddress(ChannelHandlerContext ctx) {
        SocketAddress socketAddress = ctx.channel().remoteAddress();
        if (socketAddress instanceof InetSocketAddress inetSocketAddress && inetSocketAddress.getAddress() != null) {
            return inetSocketAddress.getAddress().getHostAddress();
        }

        return socketAddress == null ? "" : socketAddress.toString().replace("/", "");
    }

    private static void writeAndClose(ChannelHandlerContext ctx, String response) {
        ChannelFuture f = ctx.channel().write(Unpooled.copiedBuffer(response.getBytes(java.nio.charset.StandardCharsets.UTF_8)), ctx.channel().voidPromise());
        ctx.channel().flush();
        ctx.flush();
        f.channel().close();
    }
}
