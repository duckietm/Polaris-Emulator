final class OptionalIncomingFixture {
    void handle() {
        int id = this.packet.readInt();
        String token = "";
        if (this.packet.bytesAvailable() > 0) {
            token = this.packet.readString();
        }
    }

    void nonTrailing() {
        String token = "";
        if (this.packet.bytesAvailable() > 0) {
            token = this.packet.readString();
        }
        int id = this.packet.readInt();
    }

    void conditional() {
        int id = this.packet.readInt();
        String token = "";
        if (id > 0) {
            token = this.packet.readString();
        }
    }
}
