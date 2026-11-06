package polaris.plugin.events.support;

public enum SupportUserAlertedReason {
    ALERT(0),

    CAUTION(1),

    KICKED(2),

    AMBASSADOR(3);

    private final int code;

    SupportUserAlertedReason(int code) {
        this.code = code;
    }

    public int getCode() {
        return this.code;
    }
}
