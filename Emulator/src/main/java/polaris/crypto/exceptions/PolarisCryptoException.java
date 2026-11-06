package polaris.crypto.exceptions;

public class PolarisCryptoException extends Exception {

    public PolarisCryptoException(String message) {
        super(message);
    }

    public PolarisCryptoException(String message, Throwable cause) {
        super(message, cause);
    }

    public PolarisCryptoException(Throwable cause) {
        super(cause);
    }
}
