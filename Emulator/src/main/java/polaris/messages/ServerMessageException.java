package polaris.messages;

public class ServerMessageException extends RuntimeException {

    public ServerMessageException() {}

    public ServerMessageException(String message) {
        super(message);
    }

    public ServerMessageException(String message, Throwable cause) {
        super(message, cause);
    }

    public ServerMessageException(Throwable cause) {
        super(cause);
    }
}
