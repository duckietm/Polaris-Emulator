package com.eu.habbo.habbohotel.items.interactions.wired;

import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.ServerMessage;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;

/** Reads back what a box's {@code serializeWiredData} writes: its picks, string and int params. */
public final class WiredLegacyWireReader {
    public final int[] furniIds;
    public final String stringParam;
    public final int[] intParams;

    private WiredLegacyWireReader(int[] furniIds, String stringParam, int[] intParams) {
        this.furniIds = furniIds;
        this.stringParam = stringParam;
        this.intParams = intParams;
    }

    public static WiredLegacyWireReader read(InteractionWired box, Room room) {
        ServerMessage message = new ServerMessage(1);
        box.serializeWiredData(message, room);
        ByteBuf in = message.get();
        try {
            in.skipBytes(6); // length, header
            in.readBoolean();
            in.readInt(); // furni limit
            int[] furni = new int[in.readInt()];
            for (int i = 0; i < furni.length; i++) furni[i] = in.readInt();
            in.readInt(); // sprite
            in.readInt(); // id
            byte[] text = new byte[in.readUnsignedShort()];
            in.readBytes(text);
            int[] ints = new int[in.readInt()];
            for (int i = 0; i < ints.length; i++) ints[i] = in.readInt();
            return new WiredLegacyWireReader(furni, new String(text, StandardCharsets.UTF_8), ints);
        } finally {
            in.release();
        }
    }
}
