package polaris.messages.incoming.trading;

import polaris.Emulator;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTrade;
import polaris.hotel.rooms.RoomUnitStatus;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.trading.TradeStartFailComposer;

public class TradeStartEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (Emulator.getIntUnixTimestamp() - this.client.getUser().getUserStats().lastTradeTimestamp > 10) {
            this.client.getUser().getUserStats().lastTradeTimestamp = Emulator.getIntUnixTimestamp();
            int userId = this.packet.readInt();

            Room room = this.client.getUser().getUserInfo().getCurrentRoom();
            if (room != null) {
                if (userId >= 0 && userId != this.client.getUser().getRoomUnit().getId()) {
                    User targetUser = room.getUserByRoomUnitId(userId);

                    boolean tradeAnywhere = this.client.getUser().hasPermission(Permission.ACC_TRADE_ANYWHERE);

                    if (!RoomTrade.TRADING_ENABLED && !tradeAnywhere) {
                        this.client.sendResponse(
                                new TradeStartFailComposer(TradeStartFailComposer.HOTEL_TRADING_NOT_ALLOWED));
                        return;
                    }

                    if ((room.getTradeMode() == 0
                                    || (room.getTradeMode() == 1
                                            && this.client
                                                            .getUser()
                                                            .getUserInfo()
                                                            .getId()
                                                    != room.getOwnerId()))
                            && !tradeAnywhere) {
                        this.client.sendResponse(
                                new TradeStartFailComposer(TradeStartFailComposer.ROOM_TRADING_NOT_ALLOWED));
                        return;
                    }

                    if (targetUser == null) return;

                    if (targetUser
                            .getUserStats()
                            .userIgnored(this.client.getUser().getUserInfo().getId())) return;

                    if (this.client.getUser().getRoomUnit().hasStatus(RoomUnitStatus.TRADING)) {
                        this.client.sendResponse(
                                new TradeStartFailComposer(TradeStartFailComposer.YOU_ALREADY_TRADING));
                        return;
                    }

                    if (!this.client.getUser().getUserStats().allowTrade()) {
                        this.client.sendResponse(new TradeStartFailComposer(TradeStartFailComposer.YOU_TRADING_OFF));
                        return;
                    }

                    if (targetUser.getRoomUnit().hasStatus(RoomUnitStatus.TRADING)) {
                        this.client.sendResponse(new TradeStartFailComposer(
                                TradeStartFailComposer.TARGET_ALREADY_TRADING,
                                targetUser.getUserInfo().getUsername()));
                        return;
                    }

                    if (!targetUser.getUserStats().allowTrade()) {
                        this.client.sendResponse(new TradeStartFailComposer(
                                TradeStartFailComposer.TARGET_TRADING_NOT_ALLOWED,
                                targetUser.getUserInfo().getUsername()));
                        return;
                    }

                    room.startTrade(this.client.getUser(), targetUser);
                }
            }
        }
    }
}
