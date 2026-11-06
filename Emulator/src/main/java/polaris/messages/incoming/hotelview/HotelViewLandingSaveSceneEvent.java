package polaris.messages.incoming.hotelview;

import polaris.Emulator;
import polaris.hotel.hotelview.HotelViewScene;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;

public class HotelViewLandingSaveSceneEvent extends MessageHandler {
    private static final int MAX_URL_LENGTH = 512;

    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() {
        User user = this.client.getUser();

        if (user == null || !user.hasPermission(Permission.ACC_HOTELVIEW_EDIT)) return;

        Emulator.getGameEnvironment()
                .getHotelViewManager()
                .saveScene(new HotelViewScene(
                        limited(this.packet.readString()),
                        limited(this.packet.readString()),
                        limited(this.packet.readString()),
                        limited(this.packet.readString()),
                        this.packet.readInt(),
                        this.packet.readInt(),
                        this.packet.readInt(),
                        this.packet.readInt(),
                        this.packet.readInt(),
                        this.packet.readInt(),
                        this.packet.readInt(),
                        this.packet.readInt(),
                        this.packet.readBoolean(),
                        this.packet.readString(),
                        this.packet.readInt(),
                        java.util.List.of()));
    }

    private static String limited(String value) {
        if (value == null) return "";

        String normalized = value.trim();
        return normalized.length() > MAX_URL_LENGTH ? normalized.substring(0, MAX_URL_LENGTH) : normalized;
    }
}
