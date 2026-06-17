package com.eu.habbo.networking.gameserver.decoders;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.messages.PacketManager;
import com.eu.habbo.threading.runnables.ChannelReadHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.UnsupportedMessageTypeException;
import io.netty.handler.ssl.NotSslRecordException;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

@ChannelHandler.Sharable
public class GameMessageHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(GameMessageHandler.class);


    @Override
    public void channelRegistered(ChannelHandlerContext ctx) {
        if (!Emulator.getGameServer().getGameClientManager().addClient(ctx)) {
            ctx.channel().close();
        }
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) {
        ctx.channel().close();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof ClientMessage)) {
            try {
                if (Emulator.getConfig().getBoolean("debug.mode")) {
                    LOGGER.debug("Discarding non-game message {} from {}",
                            msg.getClass().getSimpleName(), ctx.channel().remoteAddress());
                }
            } finally {
                ReferenceCountUtil.release(msg);
                ctx.channel().close();
            }
            return;
        }

        ClientMessage message = (ClientMessage) msg;

        try {
            // This handler is registered on a dedicated EventExecutorGroup
            // (see WebSocketChannelInitializer), so channelRead already runs OFF
            // the Netty I/O event loop, serialized per channel. Running the
            // handler inline here keeps that per-channel ordering — submitting to
            // the shared game pool instead would break ordering, so we no longer
            // branch on MULTI_THREADED_PACKET_HANDLING.
            new ChannelReadHandler(ctx, message).run();
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ctx.channel().close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof IOException) {
            ctx.channel().close();
            return;
        }
        if (Emulator.getConfig().getBoolean("debug.mode")) {
            if (cause instanceof NotSslRecordException) {
                LOGGER.error("Plaintext received instead of ssl, closing channel");
            }
            else if (cause instanceof DecoderException) {
                LOGGER.error("Plaintext received instead of ssl, closing channel");
            }
            else if (cause instanceof TooLongFrameException) {
                LOGGER.error("Disconnecting client, reason {}", cause.getMessage());
            }
            else if (cause instanceof SSLHandshakeException) {
                LOGGER.error("URL Request error from source {}", ctx.channel().remoteAddress());
            }
            else if (cause instanceof NoSuchAlgorithmException) {
                LOGGER.error("Invalid SSL algorithm, only TLSv1.2 supported in the request");
            }
            else if (cause instanceof KeyManagementException) {
                LOGGER.error("Invalid SSL algorithm, only TLSv1.2 supported in the request");
            }
            else if (cause instanceof UnsupportedMessageTypeException) {
                LOGGER.error("There was an illegal SSL request from (X-forwarded-for/CF-Connecting-IP has not being injected yet!) {}", ctx.channel().remoteAddress());
            }
            else if (cause instanceof SSLException) {
                LOGGER.error("SSL Problem: {}{}", cause.getMessage(), cause);
            }
            else {
                LOGGER.error("Disconnecting client, exception in GameMessageHandler.", cause);
            }
        }
        ctx.channel().close();
    }
}
