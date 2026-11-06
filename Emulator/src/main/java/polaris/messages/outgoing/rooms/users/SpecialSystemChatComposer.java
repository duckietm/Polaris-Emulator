package polaris.messages.outgoing.rooms.users;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * AIR 13 {@code SpecialSystemChat} (official id 1971, parser
 * {@code class_3275}): a system chat bubble on a room unit,
 * {@code RoomChatHandler.onSpecialSystemChat(userIndex, specialSystemType)}.
 */
public class SpecialSystemChatComposer extends MessageComposer {

    private final int userIndex;
    private final int specialSystemType;

    public SpecialSystemChatComposer(int userIndex, int specialSystemType) {
        this.userIndex = userIndex;
        this.specialSystemType = specialSystemType;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.SpecialSystemChatComposer);

        this.response.appendInt(this.userIndex);
        this.response.appendInt(this.specialSystemType);

        return this.response;
    }

    public int getUserIndex() {
        return this.userIndex;
    }

    public int getSpecialSystemType() {
        return this.specialSystemType;
    }
}
