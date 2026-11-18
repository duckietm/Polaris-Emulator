final class ReviewFalseGreensFixture {
    void ternaryDelegate() {
        boolean written = this.flag && RgWriter.write(this.response);
    }

    void lambdaDelegate() {
        this.items.forEach(item -> this.one.serialize(this.response));
    }

    void virtualHelper() {
        this.leaf.serialize(this.response);
    }

    void renameCollision() {
        this.collide.serialize(this.response);
    }

    void shadowedDeclaration() {
        {
            RgShadow x = this.shadow;
        }
        {
            RgOne x = this.one;
            x.serialize(this.response);
        }
    }

    void argumentRead() {
        RgPair pair = new RgPair(this.packet.readInt(), this.packet);
    }

    void storedPacket() {
        RgHolder holder = new RgHolder(this.packet);
        holder.read();
    }

    void superConstructor() {
        RgSubReader reader = new RgSubReader(this.packet);
    }

    void conditionalInsideDelegate() {
        RgFlagged flagged = new RgFlagged(this.packet, false);
    }

    private boolean flag;
    private java.util.List<Integer> items;
    private RgOne one;
    private RgLeaf leaf;
    private RgCollide collide;
    private RgShadow shadow;
}

class RgWriter {
    static boolean write(ServerMessage message) {
        message.appendString("x");
        return true;
    }
}

class RgOne {
    void serialize(ServerMessage message) {
        message.appendString("one");
    }
}

class RgShadow {
    void serialize(ServerMessage message) {
        message.appendInt(1);
        message.appendString("shadow");
    }
}

class RgSerializerBase {
    void serialize(ServerMessage message) {
        message.appendInt(1);
        this.extra(message);
    }

    void extra(ServerMessage message) {}
}

class RgLeaf extends RgSerializerBase {
    @Override
    void extra(ServerMessage message) {
        message.appendString("leaf");
    }
}

class RgCollide {
    private ServerMessage response;

    void serialize(ServerMessage message) {
        message.appendString("a");
        this.response.appendInt(1);
    }
}

class RgPair {
    RgPair(int first, ClientMessage packet) {
        String name = packet.readString();
    }
}

class RgHolder {
    private ClientMessage packet;

    RgHolder(ClientMessage message) {
        this.packet = message;
    }

    void read() {
        String name = this.packet.readString();
    }
}

class RgBaseReader {
    RgBaseReader(ClientMessage message) {
        String name = message.readString();
    }
}

class RgSubReader extends RgBaseReader {
    RgSubReader(ClientMessage message) {
        super(message);
        int id = message.readInt();
    }
}

class RgFlagged {
    RgFlagged(ClientMessage message, boolean withFlag) {
        int id = message.readInt();
        boolean flag = withFlag ? message.readBoolean() : false;
    }
}
