package polaris.messages.outgoing.inventory;

import java.util.Map;
import polaris.hotel.bots.Bot;
import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class InventoryBotsComposer extends MessageComposer {
    private final User user;

    public InventoryBotsComposer(User user) {
        this.user = user;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.InventoryBotsComposer);

        Map<Integer, Bot> userBots = this.user.getInventory().getBotsComponent().getBots();
        this.response.appendInt(userBots.size());
        for (Bot bot : userBots.values()) {
            this.response.appendInt(bot.getId());
            this.response.appendString(bot.getName());
            this.response.appendString(bot.getMotto());
            this.response.appendString(bot.getGender().toString().toLowerCase().charAt(0) + "");
            this.response.appendString(bot.getFigure());
        }

        return this.response;
    }

    public User getUser() {
        return user;
    }
}
