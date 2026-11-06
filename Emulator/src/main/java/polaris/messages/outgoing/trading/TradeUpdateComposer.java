package polaris.messages.outgoing.trading;

import polaris.hotel.items.FurnitureType;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.RoomTrade;
import polaris.hotel.rooms.RoomTradeUser;
import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class TradeUpdateComposer extends MessageComposer {
    private final RoomTrade roomTrade;

    public TradeUpdateComposer(RoomTrade roomTrade) {
        this.roomTrade = roomTrade;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.TradeUpdateComposer);
        for (RoomTradeUser roomTradeUser : this.roomTrade.getRoomTradeUsers()) {
            this.response.appendInt(roomTradeUser.getUserId());

            this.response.appendInt(roomTradeUser.getItems().size());
            for (UserItem item : roomTradeUser.getItems()) {
                this.response.appendInt(BuildersClubItemIds.toWire(item.getId()));
                this.response.appendString(item.getBaseItem().getType().code);
                this.response.appendInt(BuildersClubItemIds.toWire(item.getId()));
                this.response.appendInt(item.getBaseItem().getSpriteId());
                this.response.appendInt(0);
                this.response.appendBoolean(item.getBaseItem().allowInventoryStack() && !item.isLimited());
                item.serializeExtradata(this.response);
                this.response.appendInt(0);
                this.response.appendInt(0);
                this.response.appendInt(0);

                if (item.getBaseItem().getType() == FurnitureType.FLOOR) this.response.appendInt(0);
            }

            this.response.appendInt(roomTradeUser.getItems().size());
            this.response.appendInt(roomTradeUser.getItems().stream()
                    .mapToInt(RoomTrade::getCreditsByItem)
                    .sum());
        }
        return this.response;
    }

    public RoomTrade getRoomTrade() {
        return roomTrade;
    }
}
