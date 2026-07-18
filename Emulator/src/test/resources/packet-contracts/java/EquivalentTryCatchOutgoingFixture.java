final class EquivalentTryCatchOutgoingFixture {
    private final Response response = null;

    Object composeInternal() {
        response.appendInt(1);
        try {
            response.appendInt(Integer.parseInt("2"));
        } catch (RuntimeException exception) {
            response.appendInt(0);
        }
        response.appendString("done");
        return response;
    }
}
