final class ReaderHandlerFixture {
    void handle() {
        int id = this.packet.readInt();
        DelegateRequest request = new DelegateRequest(this.packet);
        boolean confirmed = this.packet.readBoolean();
    }

    void staticReader() {
        DelegateEnvelope envelope = DelegateParser.parseEnvelope(this.packet);
    }

    void instanceReader() {
        DelegateSettings settings = this.settings;
        settings.readFrom(this.packet);
    }

    void abstractReader() {
        DelegateAbstractReader reader = this.reader;
        reader.read(this.packet);
    }

    void handlerPassed() {
        new DelegateRequest(this);
    }

    private DelegateSettings settings;
    private DelegateAbstractReader reader;
}

class DelegateRequest {
    DelegateRequest(ClientMessage packet) {
        this.name = packet.readString();
        this.count = packet.readInt();
    }
}

class DelegateParser {
    static DelegateEnvelope parseEnvelope(ClientMessage in) {
        int revision = in.readInt();
        String token = in.readString();
        return null;
    }
}

class DelegateSettings {
    void readFrom(ClientMessage message) {
        this.enabled = message.readBoolean();
    }
}

abstract class DelegateAbstractReader {
    abstract void read(ClientMessage message);
}
