package polaris.messages.outgoing.users;

import java.util.List;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * Official {@code class_2778} / {@code class_3807} (header 2649): the ids the session blocks.
 * {@code BlockedUsersManager.onBlockList} replaces its whole cache with this list.
 */
public class BlockListComposer extends MessageComposer {
    private final List<Integer> blockedUserIds;

    public BlockListComposer(List<Integer> blockedUserIds) {
        this.blockedUserIds = List.copyOf(blockedUserIds);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.BlockListComposer);
        this.response.appendInt(this.blockedUserIds.size());

        for (Integer userId : this.blockedUserIds) {
            this.response.appendInt(userId);
        }

        return this.response;
    }
}
