package polaris.hotel.users.custombadge;

public class CustomBadgeException extends Exception {

    private final String code;

    public CustomBadgeException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return this.code;
    }
}
