package polaris.messages.outgoing.unknown;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;

/**
 * @deprecated SnowWar is implemented by the composers in {@code polaris.messages.outgoing.snowwar}; nothing
 *     sends this one. Kept for plugins.
 */
@Deprecated
public class SnowWarsFullGameStatusComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(0);
        this.response.appendInt(0); // Unused
        this.response.appendInt(0);
        this.response.appendInt(0);

        // SnowWarGameObjectData
        this.response.appendInt(1); // Count
        // {
        this.response.appendInt(3); // type
        this.response.appendInt(1); // id?

        this.response.appendInt(1); // variable
        this.response.appendInt(1); // variable
        this.response.appendInt(1); // variable
        this.response.appendInt(1); // variable
        this.response.appendInt(1); // variable
        this.response.appendInt(1); // variable
        this.response.appendInt(1); // variable

        // 1: -> 11 variables.
        // 4: -> 8 variables.
        // 3: -> 7 variables.
        // 5: -> 19 variables.
        // 2: -> 9 variables.
        // }
        return this.response;
    }
}
