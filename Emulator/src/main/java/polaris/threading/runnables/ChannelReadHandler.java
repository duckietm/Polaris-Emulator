package polaris.threading.runnables;

import io.netty.channel.ChannelHandlerContext;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.messages.ClientMessage;
import polaris.networking.gameserver.GameServerAttributes;

public class ChannelReadHandler implements Runnable {

    private final ChannelHandlerContext ctx;
    private final ClientMessage message;

    public ChannelReadHandler(ChannelHandlerContext ctx, ClientMessage message) {
        this.ctx = ctx;
        this.message = message;
    }

    public void run() {
        try {
            GameClient client =
                    this.ctx.channel().attr(GameServerAttributes.CLIENT).get();

            if (client != null) {
                Emulator.getGameServer().getPacketManager().handlePacket(client, message);
            }
        } finally {
            this.message.release();
        }
    }
}
