final class DelegatedIncomingMethodFixture {
    private final Packet packet = null;

    void handle() {
        ExternalReader.read(this.packet);
    }
}
