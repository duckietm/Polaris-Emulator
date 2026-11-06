package polaris.messages.outgoing.unknown;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.handshake.IsFirstLoginOfDayComposer;

/**
 * Header 793 is sent by {@link IsFirstLoginOfDayComposer}; this class sent the same packet with {@code false}. It stays
 * only for plugins that may still use it.
 */
@Deprecated
public class UnknownComposer4 extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        return new IsFirstLoginOfDayComposer(false).compose();
    }
}
