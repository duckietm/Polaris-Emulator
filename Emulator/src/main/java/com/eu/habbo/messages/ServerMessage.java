package com.eu.habbo.messages;

import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.util.PacketUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ServerMessage {

    private boolean initialized;

    private int header;
    private ByteBufOutputStream stream;
    private ByteBuf channelBuffer;
    private MessageComposer composer;

    public ServerMessage() {

    }

    public ServerMessage(int header) {
        this.init(header);
    }

    public ServerMessage init(int id) {
        if (this.initialized) {
            throw new ServerMessageException("ServerMessage was already initialized.");
        }

        this.initialized = true;
        this.header = id;
        this.channelBuffer = Unpooled.buffer();
        this.stream = new ByteBufOutputStream(this.channelBuffer);
        this.composer = null;

        try {
            this.stream.writeInt(0);
            this.stream.writeShort(id);
        } catch (IOException e) {
            throw new ServerMessageException(e);
        }

        return this;
    }

    public void appendRawBytes(byte[] bytes) {
        try {
            this.stream.write(bytes);
        } catch (IOException e) {
            throw new ServerMessageException(e);
        }
    }

    public void appendString(String obj) {
        if (obj == null) {
            this.appendString("");
            return;
        }

        try {
            byte[] data = obj.getBytes(StandardCharsets.UTF_8);
            this.stream.writeShort(data.length);
            this.stream.write(data);
        } catch (IOException e) {
            throw new ServerMessageException(e);
        }
    }

    public void appendChar(int obj) {
        try {
            this.stream.writeChar(obj);
        } catch (IOException e) {
            throw new ServerMessageException(e);
        }
    }

    public void appendChars(Object obj) {
        try {
            this.stream.writeChars(obj.toString());
        } catch (IOException e) {
            throw new ServerMessageException(e);
        }
    }

    public void appendInt(Integer obj) {
        try {
            this.stream.writeInt(obj);
        } catch (IOException e) {
            throw new ServerMessageException(e);
        }
    }

    public void appendInt(Short obj) {
        this.appendShort(0);
        this.appendShort(obj);
    }

    public void appendInt(Byte obj) {
        try {
            this.stream.writeInt((int) obj);
        } catch (IOException e) {
            throw new ServerMessageException(e);
        }
    }

    public void appendInt(Boolean obj) {
        try {
            this.stream.writeInt(obj ? 1 : 0);
        } catch (IOException e) {
            throw new ServerMessageException(e);
        }
    }

    public void appendShort(int obj) {
        try {
            this.stream.writeShort((short) obj);
        } catch (IOException e) {
            throw new ServerMessageException(e);
        }
    }

    public void appendByte(Integer b) {
        try {
            this.stream.writeByte(b);
        } catch (IOException e) {
            throw new ServerMessageException(e);
        }
    }

    public void appendBoolean(Boolean obj) {
        try {
            this.stream.writeBoolean(obj);
        } catch (IOException e) {
            throw new ServerMessageException(e);
        }
    }

    public void appendDouble(double d) {
        try {
            this.stream.writeDouble(d);
        } catch (IOException e) {
            throw new ServerMessageException(e);
        }
    }

    public void appendDouble(Double obj) {
        try {
            this.stream.writeDouble(obj);
        } catch (IOException e) {
            throw new ServerMessageException(e);
        }
    }

    public ServerMessage appendResponse(ServerMessage obj) {
        try {
            this.stream.write(obj.get().array());
        } catch (IOException e) {
            throw new ServerMessageException(e);
        }

        return this;
    }

    public void append(ISerialize obj) {
        obj.serialize(this);
    }

    public String getBodyString() {
        return PacketUtils.formatPacket(this.channelBuffer);
    }

    public int getHeader() {
        return this.header;
    }

    public ByteBuf get() {
        this.channelBuffer.setInt(0, this.channelBuffer.writerIndex() - 4);

        return this.channelBuffer.copy();
    }

    public MessageComposer getComposer() {
        return composer;
    }

    public void setComposer(MessageComposer composer) {
        this.composer = composer;
    }

}