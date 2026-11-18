final class SerializerComposerFixture {
    void composeInternal() {
        this.response.appendInt(1);
        this.room.serialize(this.response);
        this.response.appendBoolean(true);
    }

    void forEachList() {
        this.response.appendInt(this.rooms.size());
        for (DelegateRoom item : this.rooms) {
            item.serialize(this.response);
        }
    }

    void staticSerializer() {
        DelegateWriter.write(this.response, 5);
        this.response.appendString("x");
    }

    void abstractReceiver() {
        DelegateShape shape = this.shape;
        shape.serialize(this.response);
    }

    void overriddenReceiver() {
        DelegateBase base = this.base;
        base.serialize(this.response);
    }

    void overloadedSerializer() {
        DelegateOverloaded overloaded = this.overloaded;
        overloaded.serialize(this.response);
    }

    void recursiveSerializer() {
        DelegateNode node = this.node;
        node.serialize(this.response);
    }

    void conditionalSerializer() {
        if (this.room != null) {
            this.room.serialize(this.response);
        }
    }

    void untypedReceiver() {
        var room = this.room;
        room.serialize(this.response);
    }

    private DelegateRoom room;
    private java.util.List<DelegateRoom> rooms;
    private DelegateShape shape;
    private DelegateBase base;
    private DelegateOverloaded overloaded;
    private DelegateNode node;
}

class DelegateRoom {
    public void serialize(ServerMessage message) {
        message.appendInt(this.id);
        message.appendString(this.name);
        appendOwner(message);
    }

    private void appendOwner(ServerMessage out) {
        out.appendInt(this.ownerId);
    }
}

class DelegateWriter {
    static void write(ServerMessage message, int value) {
        message.appendInt(value);
        message.appendShort(value);
    }
}

abstract class DelegateShape {
    abstract void serialize(ServerMessage message);
}

class DelegateBase {
    void serialize(ServerMessage message) {
        message.appendInt(1);
    }
}

class DelegateDerived extends DelegateBase {
    @Override
    void serialize(ServerMessage message) {
        message.appendString("x");
    }
}

class DelegateOverloaded {
    void serialize(ServerMessage message) {
        message.appendInt(1);
    }

    void serialize(Object message) {}
}

class DelegateNode {
    private DelegateNode child;

    void serialize(ServerMessage message) {
        message.appendInt(1);
        this.child.serialize(message);
    }
}
