final class OutgoingFixture {
    void composeInternal() {
        this.response.appendInt(7);
        appendDetails();
        this.response.appendBoolean(true);
    }

    private void appendDetails() {
        this.response.appendString("name");
        this.response.appendShort(2);
    }
}
