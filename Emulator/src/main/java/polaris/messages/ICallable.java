package polaris.messages;

import polaris.messages.incoming.MessageHandler;

public interface ICallable {
    void call(MessageHandler handler);
}
