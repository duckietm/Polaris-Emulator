package com.srth.plugin.websocket;

import com.srth.plugin.websocket.Habbo.HabboClientManager;
import com.srth.plugin.websocket.Habbo.SSOClientManager;
import com.srth.plugin.websocket.communication.incoming.Incoming;
import com.srth.plugin.metrics.RoleplayMetrics;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import java.nio.charset.StandardCharsets;

public class MessageHandler extends SimpleChannelInboundHandler<BinaryWebSocketFrame> {
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        RoleplayMetrics.websocketConnected();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        RoleplayMetrics.websocketDisconnected();
        HabboClientManager.killClient(ctx.channel().id());
        SSOClientManager.removeClient(ctx.channel());
        ctx.channel().close();
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, BinaryWebSocketFrame textWebSocketFrame) throws Exception {
        RoleplayMetrics.measureChecked("websocket.incoming", () ->
                new Incoming(textWebSocketFrame.content().toString(StandardCharsets.UTF_8),
                        channelHandlerContext.channel()));
    }




}
