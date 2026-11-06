package polaris.messages.rcon;

import com.google.gson.Gson;
import jakarta.validation.constraints.Positive;
import polaris.Emulator;
import polaris.hotel.catalog.CatalogPage;
import polaris.hotel.catalog.layouts.RoomBundleLayout;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserManager;

public class SendRoomBundle extends RCONMessage<SendRoomBundle.JSON> {
    public SendRoomBundle() {
        super(JSON.class);
    }

    @Override
    public void handle(Gson gson, JSON json) {
        if (json.catalog_page > 0 && json.user_id > 0) {
            User user = Emulator.getGameEnvironment().getUserManager().getUser(json.user_id);
            CatalogPage page = Emulator.getGameEnvironment().getCatalogManager().getCatalogPage(json.catalog_page);

            if ((page instanceof RoomBundleLayout)) {
                if (user != null) {
                    ((RoomBundleLayout) page).buyRoom(user);
                } else {
                    UserInfo info = UserManager.getOfflineUserInfo(json.user_id);

                    if (info != null) {
                        ((RoomBundleLayout) page).buyRoom(null, json.user_id, info.getUsername());
                    }
                }
            }
        }
    }

    static class JSON {
        @Positive(message = "invalid user")
        public int user_id;

        @Positive(message = "invalid catalog page")
        public int catalog_page;
    }
}
