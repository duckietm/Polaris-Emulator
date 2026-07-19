package com.eu.habbo.networking;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

public final class ServerBindFailureProbe {
    private ServerBindFailureProbe() {
    }

    public static void main(String[] args) throws Exception {
        try (ServerSocket occupiedPort = new ServerSocket()) {
            occupiedPort.setReuseAddress(false);
            occupiedPort.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));

            ProbeServer server = new ProbeServer(
                    InetAddress.getLoopbackAddress().getHostAddress(),
                    occupiedPort.getLocalPort());
            server.initializePipeline();

            try {
                server.connect();
            } finally {
                server.stop();
            }
        }
    }

    private static final class ProbeServer extends Server {
        private ProbeServer(String host, int port) throws Exception {
            super("Bind Failure Probe", host, port, 1, 1);
        }

        @Override
        public void initializePipeline() {
            super.initializePipeline();
            this.serverBootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel channel) {
                    // No handlers are required to reproduce a bind collision.
                }
            });
        }
    }
}
