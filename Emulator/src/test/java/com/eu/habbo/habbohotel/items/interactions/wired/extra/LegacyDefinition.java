package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.ServerMessage;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;

/** The string and int params of a box's legacy definition, as its serializeWiredData writes them. */
record LegacyDefinition(String stringParam, int[] intParams, int code, byte[] trailing) {

    static LegacyDefinition of(InteractionWired box, Room room) {
        ServerMessage message = new ServerMessage(1);
        box.serializeWiredData(message, room);
        ByteBuf buffer = message.get();
        try {
            buffer.skipBytes(6); // length, header
            buffer.readBoolean();
            buffer.readInt(); // furni limit
            buffer.skipBytes(buffer.readInt() * 4); // picks
            buffer.readInt(); // sprite
            buffer.readInt(); // id
            byte[] text = new byte[buffer.readUnsignedShort()];
            buffer.readBytes(text);
            int[] ints = new int[buffer.readInt()];
            for (int i = 0; i < ints.length; i++) ints[i] = buffer.readInt();
            buffer.readInt(); // selection code
            int code = buffer.readInt();
            byte[] trailing = new byte[buffer.readableBytes()];
            buffer.readBytes(trailing);
            return new LegacyDefinition(new String(text, StandardCharsets.UTF_8), ints, code, trailing);
        } finally {
            buffer.release();
        }
    }
}
