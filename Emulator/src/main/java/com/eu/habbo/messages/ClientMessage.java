package com.eu.habbo.messages;

import com.eu.habbo.util.PacketUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.StandardCharsets;

public class ClientMessage {
    private final int header;
    private final ByteBuf buffer;

    public ClientMessage(int messageId, ByteBuf buffer) {
        this.header = messageId;
        this.buffer = ((buffer == null) || (buffer.readableBytes() == 0) ? Unpooled.EMPTY_BUFFER : buffer);
    }

    public ByteBuf getBuffer() {
        return this.buffer;
    }

    public int getMessageId() {
        return this.header;
    }
    
    
    /**
     *
     * @return
     * @throws CloneNotSupportedException
     */
    @Override
    public ClientMessage clone() throws CloneNotSupportedException {
        return new ClientMessage(this.header, this.buffer.duplicate());
    }

    public int readShort() {
        try {
            return this.buffer.readShort();
        } catch (Exception e) {
        }

        return 0;
    }

    public Integer readInt() {
        try {
            return this.buffer.readInt();
        } catch (Exception e) {
        }

        return 0;
    }

    public boolean readBoolean() {
        try {
            return this.buffer.readByte() == 1;
        } catch (Exception e) {
        }

        return false;
    }

    public String readString() {
        try {
            // Length is an unsigned short in the protocol; mask to avoid a
            // negative array size, and clamp to what's actually buffered so a
            // bogus length can't throw mid-read and desync the remaining fields.
            int length = this.readShort() & 0xFFFF;
            int available = this.buffer.readableBytes();
            if (length > available) {
                length = available;
            }
            byte[] data = new byte[length];
            this.buffer.readBytes(data);
            return new String(data, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    public String getMessageBody() {
        return PacketUtils.formatPacket(this.buffer);
    }

    public int bytesAvailable() {
        return this.buffer.readableBytes();
    }

    public boolean release() {
        return this.buffer.release();
    }

}