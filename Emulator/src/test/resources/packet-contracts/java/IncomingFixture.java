final class IncomingFixture {
    void handle() {
        int id = this.packet.readInt();
        readDetails();
        boolean enabled = this.packet.readBoolean();
    }

    private void readDetails() {
        String name = this.packet.readString();
        short count = this.packet.readShort();
    }
}
